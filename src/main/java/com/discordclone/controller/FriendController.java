package com.discordclone.controller;

import com.discordclone.model.Friendship;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendResponsePayload;
import com.discordclone.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendshipService friendshipService;

    @Autowired
    public FriendController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping
    public List<FriendResponsePayload> getFriends(@RequestParam Long userId) {
        return friendshipService.getFriends(userId);
    }

    @GetMapping("/requests")
    public List<FriendResponsePayload> getPendingRequests(@RequestParam Long userId) {
        return friendshipService.getPendingRequests(userId);
    }

    @PostMapping("/request")
    @PreAuthorize("authentication.principal.id == #senderId")
    public ResponseEntity<?> sendFriendRequest(
            @RequestParam Long senderId,
            @Valid @RequestBody FriendRequestPayload request) {
        Friendship friendship = friendshipService.sendFriendRequest(senderId, request);
        return ResponseEntity.ok().body(friendship);
    }

    @PutMapping("/accept/{requestId}")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> acceptFriendRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId) {
        Friendship friendship = friendshipService.acceptFriendRequest(requestId, userId);
        return ResponseEntity.ok().body(friendship);
    }

    @PutMapping("/reject/{requestId}")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> rejectFriendRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId) {
        friendshipService.rejectFriendRequest(requestId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendId}")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> removeFriend(
            @PathVariable Long friendId,
            @RequestParam Long userId) {
        friendshipService.removeFriend(userId, friendId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/block/{targetId}")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> blockUser(
            @PathVariable Long targetId,
            @RequestParam Long userId) {
        Friendship friendship = friendshipService.blockUser(userId, targetId);
        return ResponseEntity.ok().body(friendship);
    }

    @DeleteMapping("/block/{targetId}")
    @PreAuthorize("authentication.principal.id == #userId")
    public ResponseEntity<?> unblockUser(
            @PathVariable Long targetId,
            @RequestParam Long userId) {
        friendshipService.unblockUser(userId, targetId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check/{friendId}")
    public ResponseEntity<Boolean> checkFriendship(
            @PathVariable Long friendId,
            @RequestParam Long userId) {
        boolean areFriends = friendshipService.areFriends(userId, friendId);
        return ResponseEntity.ok(areFriends);
    }
}
