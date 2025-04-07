package com.discordclone.service;

import com.discordclone.model.UserStatus;
import com.discordclone.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {
    private final UserStatusService userStatusService;


    public WebSocketEventListener(UserStatusService userStatusService) {
        this.userStatusService = userStatusService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Authentication authentication = (Authentication) headerAccessor.getUser();


            // Retrieve authentication from the session
            if (authentication != null) {
                UserPrincipal userDetails = (UserPrincipal)  authentication.getPrincipal();
                Long userId = userDetails.getId();  // Assuming CustomUserDetails has getId()
                String userName = userDetails.getUsername();

                System.out.println("User Connected: " + userName);


                userStatusService.updateUserStatus(userId, UserStatus.valueOf("ONLINE"));
        }
    }
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // Retrieve authentication from the session
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null) {
            UserPrincipal userDetails = (UserPrincipal)  authentication.getPrincipal();
            Long userId = userDetails.getId();  // Assuming CustomUserDetails has getId()
            String userName = userDetails.getUsername();

            System.out.println("User Disconnected: " + userName);


            userStatusService.updateUserStatus(userId, UserStatus.valueOf("OFFLINE"));

        }
    }

}
