package com.discordclone.controller;

import com.discordclone.payload.UpdateServerRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.discordclone.payload.ServerPayload;
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
    public ResponseEntity<ServerDTO> createServer(
            @RequestBody ServerPayload server,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(ServerDTO.fromEntity(serverService.createServer(server, currentUser.getId())));
    }

    @GetMapping
    public ResponseEntity<List<ServerDTO>> getUserServers(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(serverService.getUserServers(currentUser.getId()).stream()
                .map(ServerDTO::fromEntity)
                .toList());
    }

    @GetMapping("/{serverId}")
    @PreAuthorize("@perm.isServerMember(#serverId, principal.id)")
    public ResponseEntity<ServerDTO> getServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ServerDTO.fromEntity(serverService.getServerById(serverId)));
    }

    @PostMapping("/{serverId}/members/{userId}")
    @PreAuthorize("@perm.canAddMember(#serverId, principal.id)")
    public ResponseEntity<ServerDTO> addMember(
            @PathVariable Long serverId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(ServerDTO.fromEntity(serverService.addMember(serverId, userId)));
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    @PreAuthorize("@perm.canRemoveMember(#serverId, #userId, principal.id)")
    public ResponseEntity<ServerDTO> removeMember(
            @PathVariable Long serverId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(ServerDTO.fromEntity(serverService.removeMember(serverId, userId)));
    }

    @DeleteMapping("/{serverId}")
    @PreAuthorize("@perm.isServerOwner(#serverId, principal.id)")
    public ResponseEntity<Void> deleteServer(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        serverService.deleteServer(serverId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{serverId}")
    @PreAuthorize("@perm.isServerOwner(#serverId, principal.id)")
    public ResponseEntity<ServerDTO> updateServer(
            @PathVariable Long serverId,
            @Valid @RequestBody UpdateServerRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ServerDTO.fromEntity(
                serverService.updateServer(serverId, request, currentUser.getId())));
    }

    @PutMapping("/{serverId}/roles/{roleId}")
    @PreAuthorize("@perm.isServerOwner(#serverId, principal.id)")
    public ResponseEntity<ServerDTO> updateRole(
            @PathVariable Long serverId,
            @PathVariable Long userId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ServerDTO.fromEntity(
                serverService.updateRole(serverId, userId, roleId, currentUser.getId())));
    }
}
