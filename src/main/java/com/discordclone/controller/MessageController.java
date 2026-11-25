package com.discordclone.controller;

import com.discordclone.dto.MessagePage;
import com.discordclone.dto.MessageResp;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.UpdateMessageRequest;
import com.discordclone.security.CurrentUser;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload @Valid MessageRequest message, SimpMessageHeaderAccessor headerAccessor) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal currentUser) {
            // MessageService checks that the sender may write to the channel.
            messageService.sendMessage(message, currentUser.getId());
        }
    }

    @GetMapping("/channels/{channelId}")
    @PreAuthorize("@perm.isChannelMember(#channelId, principal.id)")
    public ResponseEntity<MessagePage> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getChannelMessages(channelId, before, limit));
    }

    @PutMapping("/{messageId}")
    @PreAuthorize("@perm.isMessageAuthor(#messageId, principal.id)")
    public ResponseEntity<MessageResp> editMessage(
            @PathVariable String messageId,
            @Valid @RequestBody UpdateMessageRequest request,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(messageService.editMessage(messageId, request.content()));
    }

    @DeleteMapping("/{messageId}")
    @PreAuthorize("@perm.isMessageAuthor(#messageId, principal.id)")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        messageService.deleteMessage(messageId);
        return ResponseEntity.ok().build();
    }
}
