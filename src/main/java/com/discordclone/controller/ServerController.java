package com.discordclone.controller;

import com.discordclone.payload.ServerPayload;
import com.discordclone.model.Server;
import com.discordclone.model.ServerDTO;
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
            @RequestBody ServerPayload server,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(serverService.createServer(server, currentUser.getId()));
    }

    @GetMapping
    public ResponseEntity<List<Server>> getUserServers(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.getUserServers(currentUser.getId()));
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<ServerDTO> getServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ServerDTO.fromEntity(serverService.getServerById(serverId)));
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
    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> deleteServer(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        serverService.deleteServer(serverId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{serverId}")
    public ResponseEntity<Server> updateServer(
            @PathVariable Long serverId,
            @RequestBody Server server,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.updateServer(serverId, server, currentUser.getId()));
    }


    @PutMapping("/{serverId}/roles/{roleId}")
    public ResponseEntity<Server> updateRole(
            @PathVariable Long serverId,            
            @PathVariable Long userId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.updateRole(serverId,userId,roleId, currentUser.getId()));
    }
    

    
} 