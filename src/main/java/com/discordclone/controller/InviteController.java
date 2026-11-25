package com.discordclone.controller;

import com.discordclone.dto.InviteResponse;
import com.discordclone.model.Invite;
import com.discordclone.model.Server;
import com.discordclone.model.ServerDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    /** Origin of the web client, where invite links lead. */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/create")
    @PreAuthorize("@perm.isServerMember(#serverId, principal.id)")
    public ResponseEntity<InviteResponse> createInvite(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "10") int maxUses,
            @RequestParam(defaultValue = "1440") long validMinutes,
            @RequestParam(defaultValue = "false") boolean fresh,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Invite invite = inviteService.createInvite(
                serverId, maxUses, Duration.ofMinutes(validMinutes), currentUser.getId(), fresh);

        String inviteUrl = frontendUrl + "/invite/" + invite.getCode();

        InviteResponse response = new InviteResponse(
                inviteUrl,
                invite.getCode(),
                invite.getServer().getId(),
                invite.getMaxUses(),
                invite.getExpiry().toString()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/join/{code}")
    public ResponseEntity<ServerDTO> joinServerViaInvite(
            @PathVariable String code,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Server server = inviteService.joinViaInvite(code, currentUser.getId());
        return ResponseEntity.ok(ServerDTO.fromEntity(server));
    }
}
