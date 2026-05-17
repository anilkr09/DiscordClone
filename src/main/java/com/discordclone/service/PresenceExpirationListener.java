package com.discordclone.service;

import com.discordclone.constants.PresenceKeys;
import com.discordclone.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceExpirationListener {

    private static final String HEARTBEAT_PREFIX =
            "presence:heartbeat:";

    private final UserStatusService userStatusService;

    public void handleMessage(String key) {

        log.info("Redis key expired: {}", key);

        if (!key.startsWith(HEARTBEAT_PREFIX)) {
            return;
        }

        Long userId = extractUserId(key);

        if (userId == null) {
            return;
        }

        log.info("User {} marked OFFLINE due to heartbeat expiry", userId);

        userStatusService.setOfflineAndBroadCast(userId, UserStatus.OFFLINE);

        userStatusService.broadcastStatusChange(
                userId,
                UserStatus.OFFLINE
        );
    }

    private Long extractUserId(String key) {

        try {

            String userIdPart =
                    key.substring(HEARTBEAT_PREFIX.length());

            return Long.parseLong(userIdPart);

        } catch (Exception ex) {

            log.error(
                    "Failed to extract userId from key: {}",
                    key,
                    ex
            );

            return null;
        }
    }
}