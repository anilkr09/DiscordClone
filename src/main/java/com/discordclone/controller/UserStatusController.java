package com.discordclone.controller;

import com.discordclone.exception.ValidationException;
import java.util.Locale;
import com.discordclone.dto.SelfStatusResponse;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserStatusController {

    private final UserStatusService userStatusService;

    // =========================
    // GET SINGLE USER STATUS
    // =========================
    @GetMapping("/{userId}/status")
    public ResponseEntity<StatusResponse> getUserStatus(@PathVariable Long userId) {

        UserStatus status = userStatusService.getUserStatus(userId);

        return ResponseEntity.ok(
                new StatusResponse(userId, status.name())
        );
    }

    // =========================
    // GET CURRENT USER STATUS
    // =========================
    @GetMapping("/me/status")
    public ResponseEntity<SelfStatusResponse> getCurrentUserStatus() {

        UserPrincipal user = getCurrentUser();

        UserStatus status = userStatusService.getUserStatus(user.getId());


        return ResponseEntity.ok(
                new SelfStatusResponse(
                        status.name(),
                        status.name(),
                        ""
                )
        );
    }

    // =========================
    // GET FRIENDS STATUS (IMPORTANT)
    // =========================
    @GetMapping("/friends/status")
    public ResponseEntity<List<StatusResponse>> getFriendsStatus() {

        UserPrincipal currentUser = getCurrentUser();

        return ResponseEntity.ok(
                userStatusService.getFriendsStatus(currentUser.getId())
        );
    }

    // =========================
    // SET CUSTOM STATUS
    // =========================
    @PostMapping("/status/custom")
    public ResponseEntity<Void> setCustomStatus(@RequestParam String status) {

        UserPrincipal user = getCurrentUser();

        UserStatus custom;
        try {
            custom = UserStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown status: " + status);
        }

        userStatusService.updateCustomStatus(user.getId(), custom);

        return ResponseEntity.ok().build();
    }

    // =========================
    // CLEAR CUSTOM STATUS
    // =========================
    @DeleteMapping("/status/custom")
    public ResponseEntity<Void> clearCustomStatus() {

        UserPrincipal currentUser = getCurrentUser();

        userStatusService.clearCustomStatus(currentUser.getId());

        return ResponseEntity.ok().build();
    }

    // =========================
    // RESET PRESENCE (OPTIONAL)
    // =========================
    @DeleteMapping("/status/reset")
    public ResponseEntity<Void> resetPresence() {

        UserPrincipal currentUser = getCurrentUser();

        userStatusService.resetPresence(currentUser.getId());

        return ResponseEntity.ok().build();
    }

    // =========================
    // HELPER
    // =========================
    private UserPrincipal getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            throw new IllegalStateException("User not authenticated");
        }

        return user;
    }
}