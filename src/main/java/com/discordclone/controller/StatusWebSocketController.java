package com.discordclone.controller;

import com.discordclone.model.UserStatus;
import com.discordclone.payload.StatusUpdatePayload;
import com.discordclone.security.CurrentUser;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class StatusWebSocketController {

    private final UserStatusService userStatusService;

    @Autowired
    public StatusWebSocketController(UserStatusService userStatusService) {
        this.userStatusService = userStatusService;
    }

    @MessageMapping("/status")
    public void handleStatusUpdate(@Payload StatusUpdatePayload statusUpdate, SimpMessageHeaderAccessor headerAccessor) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
            
            userStatusService.updateUserStatus(currentUser.getId(), statusUpdate.getStatus());
            
            // If custom status is provided
            if (statusUpdate.getCustomStatusText() != null && !statusUpdate.getCustomStatusText().isEmpty()) {
                userStatusService.updateCustomStatus(
                    currentUser.getId(),
                    statusUpdate.getStatus(),
                    statusUpdate.getCustomStatusText(),
                    statusUpdate.getCustomStatusEmoji(),
                    statusUpdate.getExpiresAt()
                );
            }
        }
    }
} 