package com.discordclone.service.impl;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;
import com.discordclone.presence.PresenceNotifier;
import com.discordclone.presence.PresenceProperties;
import com.discordclone.presence.PresenceState;
import com.discordclone.presence.PresenceStore;
import com.discordclone.repository.UserRepository;
import com.discordclone.repository.UserStatusRepository;
import com.discordclone.service.FriendshipService;
import com.discordclone.service.LastSeenRecorder;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Status is derived from heartbeats, activity and the user's own choice, never stored as truth.
 * The database keeps only the chosen status, so that it survives a Redis restart.
 */
@Service
@RequiredArgsConstructor
public class UserStatusServiceImpl implements UserStatusService {

    private final UserRepository userRepository;
    private final UserStatusRepository userStatusRepository;
    private final FriendshipService friendService;
    private final PresenceStore store;
    private final PresenceNotifier notifier;
    private final PresenceProperties properties;
    private final LastSeenRecorder lastSeenRecorder;
    private final Clock clock;

    @Override
    public void connected(Long userId, String sessionId) {
        Instant now = clock.instant();
        restoreCustomStatus(userId, now);
        store.connected(userId, sessionId, now);
        store.heartbeat(userId, now);
        store.activity(userId, now);
        announce(userId, now);
    }

    @Override
    public void disconnected(Long userId, String sessionId) {
        // Nothing is announced yet: the sweeper does so if no socket returns within the grace period.
        store.disconnected(userId, sessionId, clock.instant());
        lastSeenRecorder.record(userId);
    }

    @Override
    public void handleHeartbeat(Long userId, String sessionId) {
        Instant now = clock.instant();
        restoreCustomStatus(userId, now);
        store.connected(userId, sessionId, now);
        store.heartbeat(userId, now);
        announce(userId, now);
    }

    @Override
    public void handleActivity(Long userId) {
        Instant now = clock.instant();
        Instant last = store.read(userId).lastActivity();
        if (last != null && now.isBefore(last.plus(properties.activityThrottle()))) {
            return;
        }
        store.activity(userId, now);
        store.heartbeat(userId, now);
        announce(userId, now);
    }

    @Override
    public void expireDeparted() {
        store.sweep(clock.instant()).forEach(userId -> {
            if (store.announce(userId, UserStatus.OFFLINE)) {
                notifier.notifyFriends(userId, UserStatus.OFFLINE);
            }
        });
    }

    @Override
    public UserStatus getUserStatus(Long userId) {
        return store.read(userId).resolve(clock.instant(), properties);
    }

    @Override
    public List<StatusResponse> getFriendsStatus(Long userId) {
        return friendService.getFriendIds(userId).stream()
                .map(friendId -> new StatusResponse(friendId, getUserStatus(friendId).name()))
                .toList();
    }

    /** Choosing ONLINE means going back to automatic status. */
    @Override
    @Transactional
    public User updateCustomStatus(Long userId, UserStatus customStatus) {
        if (customStatus == UserStatus.ONLINE) {
            return clearCustomStatus(userId);
        }
        User user = findUser(userId);
        Instant now = clock.instant();
        Instant until = now.plus(properties.customTtl());

        UserStatusEntity entity = userStatusRepository.findByUserId(userId).orElse(new UserStatusEntity());
        entity.setUserId(userId);
        entity.setCustomStatus(customStatus);
        entity.setStatusExpiresAt(until);
        userStatusRepository.save(entity);

        store.setCustom(userId, customStatus, until);
        announce(userId, now);
        return user;
    }

    @Override
    @Transactional
    public User clearCustomStatus(Long userId) {
        User user = findUser(userId);
        userStatusRepository.findByUserId(userId).ifPresent(entity -> {
            entity.setCustomStatus(null);
            entity.setStatusExpiresAt(null);
            userStatusRepository.save(entity);
        });
        store.clearCustom(userId);
        announce(userId, clock.instant());
        return user;
    }

    @Override
    @Transactional
    public void resetPresence(Long userId) {
        clearCustomStatus(userId);
        store.forget(userId);
        notifier.notifyFriends(userId, UserStatus.OFFLINE);
        lastSeenRecorder.record(userId);
    }

    private void restoreCustomStatus(Long userId, Instant now) {
        if (!store.markLoaded(userId)) {
            return;
        }
        userStatusRepository.findByUserId(userId)
                .filter(entity -> entity.getCustomStatus() != null && entity.getStatusExpiresAt() != null
                        && entity.getStatusExpiresAt().isAfter(now))
                .ifPresent(entity -> store.setCustom(userId, entity.getCustomStatus(), entity.getStatusExpiresAt()));
    }

    private void announce(Long userId, Instant now) {
        PresenceState state = store.read(userId);
        UserStatus status = state.resolve(now, properties);
        if (store.announce(userId, status)) {
            notifier.notifyFriends(userId, status);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
