package com.discordclone.controller;

import com.discordclone.model.Channel;
import com.discordclone.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("/servers/{serverId}")
    public ResponseEntity<Channel> createChannel(
            @PathVariable Long serverId,
            @RequestBody Channel channel) {
        return ResponseEntity.ok(channelService.createChannel(channel, serverId));
    }

    @GetMapping("/servers/{serverId}")
    public ResponseEntity<List<Channel>> getServerChannels(
            @PathVariable Long serverId) {
        return ResponseEntity.ok(channelService.getServerChannels(serverId));
    }

    @GetMapping("/{channelId}")
    public ResponseEntity<Channel> getChannel(@PathVariable Long channelId) {
        return ResponseEntity.ok(channelService.getChannelById(channelId));
    }

    @PutMapping("/{channelId}")
    public ResponseEntity<Channel> updateChannel(
            @PathVariable Long channelId,
            @RequestBody Channel channelDetails) {
        return ResponseEntity.ok(channelService.updateChannel(channelId, channelDetails));
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long channelId) {
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
} 