package com.discordclone.controller;

import com.discordclone.security.UserPrincipal;
import com.discordclone.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmController {

    private final ChannelService channelService;

    /** Any user may open a DM with another; the caller is always one of the two participants. */
    @PostMapping("/{userId}")
    public ResponseEntity<Long> getOrCreateDmChannel(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.getOrCreateDmChannel(userPrincipal.getId(), userId).getId());
    }
}
