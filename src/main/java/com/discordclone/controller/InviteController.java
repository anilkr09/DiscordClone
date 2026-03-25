package com.discordclone.controller;

import com.discordclone.dto.InviteResponse;
import com.discordclone.model.Invite;
import com.discordclone.model.Server;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping("/create")
    public ResponseEntity<InviteResponse> createInvite(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "10") int maxUses,
            @RequestParam(defaultValue = "1440") long validMinutes,
            HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Invite invite = inviteService.createInvite(serverId, maxUses, Duration.ofMinutes(validMinutes), currentUser.getId());

        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
        String inviteUrl = baseUrl + "api/invites/join" + invite.getCode();

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
    public ResponseEntity<Server> joinServerViaInvite(
            @PathVariable String code,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Server server = inviteService.joinViaInvite(code, currentUser.getId());
        return ResponseEntity.ok(server);
    }
}
