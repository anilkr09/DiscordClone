package com.discordclone.controller;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;
import com.discordclone.payload.StatusUpdatePayload;
import com.discordclone.security.CurrentUser;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserService;
import com.discordclone.service.UserStatusService;
import com.discordclone.repository.UserStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserStatusController {

    private final UserService userService;
    private final UserStatusService userStatusService;
    private final UserStatusRepository userStatusRepository;

    @Autowired
    public UserStatusController(UserService userService, 
                              UserStatusService userStatusService,
                              UserStatusRepository userStatusRepository) {
        this.userService = userService;
        this.userStatusService = userStatusService;
        this.userStatusRepository = userStatusRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getAllUserStatus() {
            List<UserStatusEntity> statuses = userStatusService.getAllUserStatus();

            List<StatusResponse> res =statuses.stream().map(status -> {
                return new StatusResponse( status.getUser().getId(),
                        String.valueOf((status.getCustomStatus() != null )
                                ? status.getCustomStatus()
                                : status.getCurrentStatus())
                );

            }).collect(Collectors.toList());
       return ResponseEntity.ok(res);
        }



    @GetMapping("/{userId}/status")
    public ResponseEntity<?> getUserStatus(@PathVariable Long userId) {
        UserStatusEntity statusEntity = userStatusRepository.findById(userId.toString())
                .orElse(null);
        
        if (statusEntity != null) {
            StatusUpdatePayload payload = new StatusUpdatePayload(
                statusEntity.getCurrentStatus(),
                statusEntity.getCustomStatus(),
                statusEntity.getStatusExpiresAt() != null ? 
                    statusEntity.getStatusExpiresAt().toString() : null,
                statusEntity.getLastActivity() != null ?
                    statusEntity.getLastActivity().toString() : null,
                statusEntity.getLastStatusChange() != null ?
                    statusEntity.getLastStatusChange().toString() : null
            );
            return ResponseEntity.ok(payload);
        }
        
        // Fallback to basic status
        UserStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(new StatusUpdatePayload(status));
    }

    @GetMapping("/me/status")
    public ResponseEntity<?> getCurrentUserStatus() {
        UserPrincipal currentUser = getCurrentUser();
        return getUserStatus(currentUser.getId());
    }

    @DeleteMapping("/status/custom")
    public ResponseEntity<?> clearCustomStatus() {
        UserPrincipal currentUser = getCurrentUser();
        User user = userStatusService.clearCustomStatus(currentUser.getId());
        return ResponseEntity.ok(user);
    }
    
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalStateException("User not authenticated");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }
} 