package com.discordclone.controller;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.payload.StatusUpdatePayload;
import com.discordclone.security.CurrentUser;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserService;
import com.discordclone.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserStatusController {

    private final UserService userService;
    private final UserStatusService userStatusService;

    @Autowired
    public UserStatusController(UserService userService, UserStatusService userStatusService) {
        this.userService = userService;
        this.userStatusService = userStatusService;
    }

    @PutMapping("/status")
    public ResponseEntity<?> updateStatus(@RequestBody StatusUpdatePayload statusUpdate) {
        UserPrincipal currentUser = getCurrentUser();
        User user = userStatusService.updateUserStatus(currentUser.getId(), statusUpdate.getStatus());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{userId}/status")
    public ResponseEntity<?> getUserStatus(@PathVariable Long userId) {
        UserStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(new StatusUpdatePayload(status));
    }

    @GetMapping("/me/status")
    public ResponseEntity<?> getCurrentUserStatus() {
        UserPrincipal currentUser = getCurrentUser();
        UserStatus status = userStatusService.getUserStatus(currentUser.getId());
        return ResponseEntity.ok(new StatusUpdatePayload(status));
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