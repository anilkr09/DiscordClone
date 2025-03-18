package com.discordclone.controller;

import com.discordclone.model.UserStatus;
import com.discordclone.payload.StatusUpdatePayload;
import com.discordclone.security.CurrentUser;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class StatusWebSocketController {
    private final UserStatusService userStatusService;
    private static final Logger logger = LoggerFactory.getLogger(StatusWebSocketController.class);

    @Autowired
    public StatusWebSocketController(UserStatusService userStatusService) {
        this.userStatusService = userStatusService;
    }

    @MessageMapping("/status")
//    @SendTo("/topic/status")

    public void handleStatusUpdate(@Payload StatusUpdatePayload statusUpdate, SimpMessageHeaderAccessor headerAccessor) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
            logger.info("status update -- {}",statusUpdate.getCurrentStatus());

            // If custom status is provided
            if (statusUpdate.getCustomStatus() != null ) {
                userStatusService.updateCustomStatus(
                    currentUser.getId(),
                    statusUpdate.getCurrentStatus(),
                    statusUpdate.getCustomStatus(),
                    statusUpdate.getExpiresAt()
                );
            }
            else
                userStatusService.updateUserStatus(currentUser.getId(), statusUpdate.getCurrentStatus());


        }
    }

} 