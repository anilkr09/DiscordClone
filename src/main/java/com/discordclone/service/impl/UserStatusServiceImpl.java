package com.discordclone.service.impl;

import com.discordclone.constants.PresenceKeys;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;
import com.discordclone.repository.UserRepository;
import com.discordclone.repository.UserStatusRepository;
import com.discordclone.service.FriendshipService;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatusServiceImpl implements UserStatusService {

    private final UserRepository userRepository;
    private final UserStatusRepository userStatusRepository;
    private final FriendshipService friendService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    // =========================
    // HEARTBEAT (connection alive)
    // =========================
    @Override
    public void handleHeartbeat(Long userId) {

        redisTemplate.opsForValue().set(
                PresenceKeys.lastSeen(userId),
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(60)
        );

        updateAndBroadcast(userId);
    }

    // =========================
    // ACTIVITY (user interaction)
    // =========================
    @Override
    public void handleActivity(Long userId) {

        long now = System.currentTimeMillis();

        String key = PresenceKeys.lastActivity(userId);
        String existing = redisTemplate.opsForValue().get(key);

        // skip noisy updates (<5s)
        if (existing != null) {
            long diff = now - Long.parseLong(existing);
            if (diff < 5000) return;
        }

        redisTemplate.opsForValue().set(
                key,
                String.valueOf(now),
                Duration.ofMinutes(10)
        );

        // also ensure last_seen stays fresh
        redisTemplate.opsForValue().set(
                PresenceKeys.lastSeen(userId),
                String.valueOf(now),
                Duration.ofSeconds(60)
        );

        updateAndBroadcast(userId);
    }

    // =========================
    // STATUS RESOLUTION
    // =========================
    private UserStatus resolveStatus(Long userId) {

        // custom override
        String custom = redisTemplate.opsForValue().get(PresenceKeys.custom(userId));
        if (custom != null) {
            return UserStatus.valueOf(custom);
        }

        String lastSeen = redisTemplate.opsForValue().get(PresenceKeys.lastSeen(userId));
        if (lastSeen == null) return UserStatus.OFFLINE;

        long now = System.currentTimeMillis();

        long seenDiff = now - Long.parseLong(lastSeen);
        if (seenDiff > 60_000) return UserStatus.OFFLINE;

        String lastActivity = redisTemplate.opsForValue().get(PresenceKeys.lastActivity(userId));
        if (lastActivity == null) return UserStatus.IDLE;

        long activityDiff = now - Long.parseLong(lastActivity);

        if (activityDiff < 30_000) return UserStatus.ONLINE;
        if (activityDiff < 300_000) return UserStatus.IDLE;

        return UserStatus.IDLE;
    }

    private void updateAndBroadcast(Long userId) {

        UserStatus newStatus = resolveStatus(userId);

        String cached = redisTemplate.opsForValue().get(PresenceKeys.status(userId));

        // skip if no change
        if (cached != null && cached.equals(newStatus.name())) {
            return;
        }

        redisTemplate.opsForValue().set(
                PresenceKeys.status(userId),
                newStatus.name(),
                Duration.ofSeconds(60)
        );

        broadcastStatusChange(userId, newStatus);
    }

    // =========================
    // GET STATUS
    // =========================
    @Override
    public UserStatus getUserStatus(Long userId) {

        String cached = redisTemplate.opsForValue().get(PresenceKeys.status(userId));

        if (cached != null) {
            return UserStatus.valueOf(cached);
        }

        return resolveStatus(userId);
    }

    // =========================
    // FRIEND STATUS (BATCH)
    // =========================
    @Override
    public List<StatusResponse> getFriendsStatus(Long userId) {

        List<Long> friendIds = friendService.getFriendIds(userId);

        List<String> keys = friendIds.stream()
                .map(PresenceKeys::status)
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        List<StatusResponse> result = new ArrayList<>();

        for (int i = 0; i < friendIds.size(); i++) {

            Long fid = friendIds.get(i);
            String val = values.get(i);

            UserStatus status = (val != null)
                    ? UserStatus.valueOf(val)
                    : resolveStatus(fid);

            result.add(new StatusResponse(fid, status.name()));
        }

        return result;
    }

    // =========================
    // CUSTOM STATUS
    // =========================
    @Override
    @Transactional
    public User updateCustomStatus(Long userId, UserStatus customStatus) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // special rule
        if (customStatus == UserStatus.ONLINE) {
            return clearCustomStatus(userId);
        }

        // Redis
        redisTemplate.opsForValue().set(
                PresenceKeys.custom(userId),
                customStatus.name(),
                Duration.ofHours(24)
        );

        // DB
        UserStatusEntity entity = userStatusRepository.findByUserId(userId)
                .orElse(new UserStatusEntity());

        entity.setUserId(userId);
        entity.setCustomStatus(customStatus);
        entity.setStatusExpiresAt(LocalDateTime.now().plusDays(1));

        userStatusRepository.save(entity);

        updateAndBroadcast(userId);

        return user;
    }

    @Override
    @Transactional
    public User clearCustomStatus(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        redisTemplate.delete(PresenceKeys.custom(userId));

        userStatusRepository.findByUserId(userId).ifPresent(entity -> {
            entity.setCustomStatus(null);
            entity.setStatusExpiresAt(null);
            userStatusRepository.save(entity);
        });

        updateAndBroadcast(userId);

        return user;
    }

    // =========================
    // RESET
    // =========================
    @Override
    public void resetPresence(Long userId) {

        redisTemplate.delete(PresenceKeys.lastSeen(userId));
        redisTemplate.delete(PresenceKeys.lastActivity(userId));
        redisTemplate.delete(PresenceKeys.status(userId));
        redisTemplate.delete(PresenceKeys.custom(userId));

        broadcastStatusChange(userId, UserStatus.OFFLINE);

        persistLastSeen(userId);
    }

    // =========================
    // DB persistence
    // =========================
    @Override
    @Async
    public void persistLastSeen(Long userId) {

        UserStatusEntity entity = userStatusRepository.findByUserId(userId)
                .orElse(new UserStatusEntity());

        entity.setUserId(userId);
        entity.setLastActivity(LocalDateTime.now());

        userStatusRepository.save(entity);
    }

    // =========================
    // WS broadcast
    // =========================
    @Override
    public void broadcastStatusChange(Long userId, UserStatus status) {

        var payload = new java.util.HashMap<String, Object>();
        payload.put("userId", userId);
        payload.put("status", status);

        messagingTemplate.convertAndSend("/topic/status", payload);
    }
    @Override
    public UserStatusEntity getUserStatusEntity(Long userId) {
        return userStatusRepository.findByUserId(userId).orElse(null);
    }
}