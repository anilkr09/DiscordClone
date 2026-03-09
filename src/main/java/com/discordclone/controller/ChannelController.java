package com.discordclone.controller;

import com.discordclone.model.Channel;
import com.discordclone.model.ChannelDTO;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/servers/{serverId}/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("")
    public ResponseEntity<Channel> createChannel(
            @PathVariable Long serverId,
            @RequestBody Channel channel,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.createChannel(channel, serverId, userPrincipal.getId()));
    }

    @PostMapping("/dm/{userId}")
    public ResponseEntity<Long> getOrCreateDmChannel(
        @PathVariable Long userId,
        @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.getOrCreateDmChannel(userPrincipal.getId(), userId).getId());
    }
//    @Transactional
//    public Channel getOrCreateDmChannel(Long userId1, Long userId2) {
//
//        Long min = Math.min(userId1, userId2);
//        Long max = Math.max(userId1, userId2);
//
//        String channelName = "dm-" + min + "-" + max;
//
//        // 1️⃣ Fast path: already exists
//        return channelRepository.findByName(channelName)
//                .orElseGet(() -> createDmSafely(channelName));
//    }
//
//    private Channel createDmSafely(String channelName) {
//
//        Channel channel = Channel.builder()
//                .name(channelName)
//                .type(ChannelType.DM)
//                .server(serverService.getServerById(1L)) // DM server
//                .build();
//
//        try {
//            // 2️⃣ Try to insert
//            return channelRepository.saveAndFlush(channel);
//
//        } catch (DataIntegrityViolationException ex) {
//            // 3️⃣ Lost the race → fetch existing channel
//            return channelRepository.findByName(channelName)
//                    .orElseThrow(() ->
//                            new IllegalStateException(
//                                    "Channel exists but could not be retrieved", ex
//                            )
//                    );
//        }
//    }




@GetMapping("")
    public ResponseEntity<List<ChannelDTO>> getServerChannels(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.getServerChannels(serverId, userPrincipal.getId())
                .stream()
                .map(ChannelDTO::fromEntityWithoutMessages)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{channelId}")
    public ResponseEntity<Channel> getChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.getChannelById(channelId, userPrincipal.getId()));
    }

    @PutMapping("/{channelId}")
    public ResponseEntity<Channel> updateChannel(
            @PathVariable Long channelId,
            @RequestBody Channel channelDetails,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(channelService.updateChannel(channelId, channelDetails, userPrincipal.getId()));
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        channelService.deleteChannel(channelId, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }
}