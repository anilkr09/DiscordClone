package com.discordclone.controller;

import com.discordclone.model.Friendship;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendResponsePayload;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public List<FriendResponsePayload> getFriends() {
        UserPrincipal currentUser = getCurrentUser();
        return friendshipService.getFriends(currentUser.getId());
    }

    @GetMapping("/requests")
    public List<FriendResponsePayload> getPendingRequests() {
        UserPrincipal currentUser = getCurrentUser();
        return friendshipService.getPendingRequests(currentUser.getId());
    }

    @PostMapping("/request")
    public ResponseEntity<?> sendFriendRequest(@Valid @RequestBody FriendRequestPayload request) {
        UserPrincipal currentUser = getCurrentUser();
        Friendship friendship = friendshipService.sendFriendRequest(currentUser.getId(), request);
        return ResponseEntity.ok().body(friendship);
    }

    @PutMapping("/accept/{requestId}")
    public ResponseEntity<?> acceptFriendRequest(@PathVariable Long requestId) {
        UserPrincipal currentUser = getCurrentUser();
        Friendship friendship = friendshipService.acceptFriendRequest(requestId, currentUser.getId());
        return ResponseEntity.ok().body(friendship);
    }

    @PutMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectFriendRequest(@PathVariable Long requestId) {
        UserPrincipal currentUser = getCurrentUser();
        friendshipService.rejectFriendRequest(requestId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> removeFriend(@PathVariable Long friendId) {
        UserPrincipal currentUser = getCurrentUser();
        friendshipService.removeFriend(currentUser.getId(), friendId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/block/{targetId}")
    public ResponseEntity<?> blockUser(@PathVariable Long targetId) {
        UserPrincipal currentUser = getCurrentUser();
        Friendship friendship = friendshipService.blockUser(currentUser.getId(), targetId);
        return ResponseEntity.ok().body(friendship);
    }

    @DeleteMapping("/block/{targetId}")
    public ResponseEntity<?> unblockUser(@PathVariable Long targetId) {
        UserPrincipal currentUser = getCurrentUser();
        friendshipService.unblockUser(currentUser.getId(), targetId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check/{friendId}")
    public ResponseEntity<Boolean> checkFriendship(@PathVariable Long friendId) {
        UserPrincipal currentUser = getCurrentUser();
        boolean areFriends = friendshipService.areFriends(currentUser.getId(), friendId);
        return ResponseEntity.ok(areFriends);
    }
    
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalStateException("User not authenticated");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }
}
