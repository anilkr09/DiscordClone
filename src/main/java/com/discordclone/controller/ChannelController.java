package com.discordclone.controller;

import com.discordclone.payload.UpdateChannelRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import com.discordclone.model.ChannelDTO;
import com.discordclone.payload.ChannelPayload;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/channels/{serverId}")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("")
    @PreAuthorize("@perm.isServerAdmin(#serverId, principal.id)")
    public ResponseEntity<ChannelDTO> createChannel(
            @PathVariable Long serverId,
            @Valid @RequestBody ChannelPayload channel,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.createChannel(channel, serverId, userPrincipal.getId()));
    }

    @GetMapping("")
    @PreAuthorize("@perm.isServerMember(#serverId, principal.id)")
    public ResponseEntity<List<ChannelDTO>> getServerChannels(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.getServerChannels(serverId, userPrincipal.getId())
                .stream()
                .map(ChannelDTO::fromEntityWithoutMessages)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{channelId}")
    @PreAuthorize("@perm.isChannelMember(#channelId, principal.id)")
    public ResponseEntity<ChannelDTO> getChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId) {
        return ResponseEntity.ok(ChannelDTO.fromEntityWithoutMessages(
                channelService.getChannel(serverId, channelId)));
    }

    @PutMapping("/{channelId}")
    @PreAuthorize("@perm.isServerAdmin(#serverId, principal.id)")
    public ResponseEntity<ChannelDTO> updateChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelRequest request) {
        return ResponseEntity.ok(ChannelDTO.fromEntityWithoutMessages(
                channelService.updateChannel(serverId, channelId, request)));
    }

    @DeleteMapping("/{channelId}")
    @PreAuthorize("@perm.isServerAdmin(#serverId, principal.id)")
    public ResponseEntity<Void> deleteChannel(
            @PathVariable Long serverId,
            @PathVariable Long channelId) {
        channelService.deleteChannel(serverId, channelId);
        return ResponseEntity.ok().build();
    }
}
