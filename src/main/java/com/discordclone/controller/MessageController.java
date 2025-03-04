package com.discordclone.controller;

import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ChannelRepository channelRepository;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Message message) {
        messageService.sendMessage(message);
    }

    @GetMapping("/channels/{channelId}")
    public ResponseEntity<Page<Message>> getChannelMessages(
            @PathVariable Long channelId,
            Pageable pageable) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
        return ResponseEntity.ok(messageService.getChannelMessages(channel, pageable));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<Message> editMessage(
            @PathVariable Long messageId,
            @RequestBody Message message) {
        return ResponseEntity.ok(messageService.editMessage(message));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        Message message = messageService.getChannelMessages(null, null)
                .getContent()
                .stream()
                .filter(m -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Message not found"));
        messageService.deleteMessage(message);
        return ResponseEntity.ok().build();
    }
} 