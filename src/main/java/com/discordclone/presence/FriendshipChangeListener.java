package com.discordclone.presence;

import com.discordclone.repository.UserRepository;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FriendshipChangeListener {

    private final FriendDirectory friends;
    private final PresenceNotifier notifier;
    private final UserStatusService statuses;
    private final UserRepository users;

    // After the commit, so the cache cannot be refilled with the old list.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(FriendshipChanged change) {
        friends.invalidate(change.firstUserId());
        friends.invalidate(change.secondUserId());
        introduce(change.firstUserId(), change.secondUserId());
        introduce(change.secondUserId(), change.firstUserId());
    }

    // New friends learn each other's status at once instead of at the next change.
    private void introduce(Long about, Long to) {
        users.findById(to).ifPresent(recipient -> {
            if (friends.friendNames(about).contains(recipient.getUsername())) {
                notifier.notifyUser(recipient.getUsername(), about, statuses.getUserStatus(about));
            }
        });
    }
}
