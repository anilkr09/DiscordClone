package com.discordclone.service;

import com.discordclone.model.UserStatusEntity;
import com.discordclone.repository.UserStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** A separate bean, so that callers go through the proxy and {@code @Async} applies. */
@Component
@RequiredArgsConstructor
public class LastSeenRecorder {

    private final UserStatusRepository userStatusRepository;

    @Async
    public void record(Long userId) {
        UserStatusEntity entity = userStatusRepository.findByUserId(userId)
                .orElse(new UserStatusEntity());
        entity.setUserId(userId);
        entity.setLastActivity(Instant.now());
        userStatusRepository.save(entity);
    }
}
