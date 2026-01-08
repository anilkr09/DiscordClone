package com.discordclone.controller;

import com.discordclone.dto.MessageResp;
import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.payload.MessageRequest;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ChannelRepository channelRepository;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload MessageRequest message , SimpMessageHeaderAccessor headerAccessor) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal currentUser) {


            messageService.sendMessage(message, currentUser.getId());
        }
    }



    @GetMapping("/channels/{channelId}")

    public ResponseEntity<Page<MessageResp>> getChannelMessages(
            @PathVariable Long channelId,        @PageableDefault(page = 0, size = 20, sort = "timestamp")

            Pageable pageable) {

        log.info(
                "➡️ GET /channels/{} | page={}, size={}, sort={}",
                channelId,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> {
                    log.warn("❌ Channel not found: {}", channelId);
                    return new RuntimeException("Channel not found");
                });

        Page<MessageResp> messagesPage =
                messageService.getChannelMessages1(channel, pageable);


        log.info("⬅️ GET /channels/{} completed", channelId);


        return ResponseEntity.ok(messagesPage);

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