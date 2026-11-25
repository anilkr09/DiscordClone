package com.discordclone.presence;

import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Announces departures. Every instance may run it; each departure is claimed by exactly one. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceSweeper {

    private final UserStatusService statuses;

    @Scheduled(fixedDelayString = "${app.presence.sweep-interval:PT1S}")
    public void sweep() {
        try {
            statuses.expireDeparted();
        } catch (DataAccessException ex) {
            log.warn("Presence sweep skipped: {}", ex.getMessage());
        }
    }
}
