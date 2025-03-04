package com.discordclone.controller;

import com.discordclone.model.Server;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @PostMapping
    public ResponseEntity<Server> createServer(
            @RequestBody Server server,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.createServer(server, currentUser.getId()));
    }

    @GetMapping
    public ResponseEntity<List<Server>> getUserServers(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.getUserServers(currentUser.getId()));
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<Server> getServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(serverService.getServerById(serverId));
    }

    @PostMapping("/{serverId}/members/{userId}")
    public ResponseEntity<Server> addMember(
            @PathVariable Long serverId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(serverService.addMember(serverId, userId));
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    public ResponseEntity<Server> removeMember(
            @PathVariable Long serverId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(serverService.removeMember(serverId, userId));
    }
} 