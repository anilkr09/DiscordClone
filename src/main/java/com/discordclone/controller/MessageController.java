package com.discordclone.controller;

import com.discordclone.dto.MessageResp;
import com.discordclone.exception.ChannelAccessDeniedException;
import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.payload.MessageRequest;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.security.ChannelAuthorizationService;
import com.discordclone.security.CurrentUser;
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
    private final ChannelAuthorizationService channelAuthService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload MessageRequest message, SimpMessageHeaderAccessor headerAccessor) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal currentUser) {
            
            // Verify channel access before sending
            Channel channel = channelRepository.findById(message.getChannelId())
                    .orElseThrow(() -> new ChannelAccessDeniedException("Channel not found"));
            
            channelAuthService.requireAccess(channel, currentUser.getId());

            messageService.sendMessage(message, currentUser.getId());
        }
    }



    @GetMapping("/channels/{channelId}")

    public ResponseEntity<Page<MessageResp>> getChannelMessages(
            @PathVariable Long channelId,
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(page = 0, size = 20, sort = "timestamp")
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

        // Check channel authorization
        channelAuthService.requireAccess(channel, currentUser.getId());

        Page<MessageResp> messagesPage =
                messageService.getChannelMessages1(channel, pageable);


        log.info("⬅️ GET /channels/{} completed", channelId);


        return ResponseEntity.ok(messagesPage);

    }


    @PutMapping("/{messageId}")
    public ResponseEntity<Message> editMessage(
            @PathVariable Long messageId,
            @RequestBody Message message,
            @CurrentUser UserPrincipal currentUser) {
        
        // Verify the user owns the message
        Message existingMessage = messageService.getMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        
        if (!existingMessage.getSender().getId().equals(currentUser.getId())) {
            throw new ChannelAccessDeniedException("You can only edit your own messages");
        }
        
        // Verify channel access
        channelAuthService.requireAccess(existingMessage.getChannel(), currentUser.getId());
        
        return ResponseEntity.ok(messageService.editMessage(message));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @CurrentUser UserPrincipal currentUser) {
        
        Message message = messageService.getMessageById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        
        // Verify the user owns the message (or is admin/mod - could be extended)
        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new ChannelAccessDeniedException("You can only delete your own messages");
        }
        
        // Verify channel access
        channelAuthService.requireAccess(message.getChannel(), currentUser.getId());
        
        messageService.deleteMessage(message);
        return ResponseEntity.ok().build();
    }
} 