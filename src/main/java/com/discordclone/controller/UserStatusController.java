package com.discordclone.controller;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.payload.StatusUpdatePayload;
import com.discordclone.service.UserService;
import com.discordclone.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PutMapping("/{userId}/status")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long userId,
            @RequestBody StatusUpdatePayload statusUpdate) {
        User user = userStatusService.updateUserStatus(userId, statusUpdate.getStatus());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{userId}/status")
    public ResponseEntity<?> getUserStatus(@PathVariable Long userId) {
        UserStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(new StatusUpdatePayload(status));
    }

    @GetMapping("/me/status")
    public ResponseEntity<?> getCurrentUserStatus(@RequestParam Long userId) {
        UserStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(new StatusUpdatePayload(status));
    }

    @DeleteMapping("/{userId}/status/custom")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> clearCustomStatus(@PathVariable Long userId) {
        User user = userStatusService.clearCustomStatus(userId);
        return ResponseEntity.ok(user);
    }
} 