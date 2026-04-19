package com.discordclone.controller;

import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StatusWebSocketController {

    private final UserStatusService userStatusService;

    @MessageMapping("/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        if (userId != null) userStatusService.handleHeartbeat(userId);
    }

    @MessageMapping("/activity")
    public void activity(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        if (userId != null) userStatusService.handleActivity(userId);
    }

    private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal u) {
            return u.getId();
        }
        return null;
    }
}