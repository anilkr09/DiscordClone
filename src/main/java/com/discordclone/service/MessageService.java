package com.discordclone.service;

import com.discordclone.dto.MessageResp;
import com.discordclone.exception.ChannelAccessDeniedException;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.ChannelType;
import com.discordclone.model.FriendshipStatus;
import com.discordclone.repository.FriendshipRepository;
import com.discordclone.security.PermissionService;
import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import com.discordclone.payload.UserDTO;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.repository.MessageRepository;
import com.discordclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private  final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final MessagePersistenceService persistenceService;
    private final MessageEventPublisher eventPublisher;
    private final FriendshipRepository friendshipRepository;
    private final PermissionService permissions;
    @Transactional
    public Message sendMessage(MessageRequest request, Long userId) {

        Message message = new Message();

        User user = findUser(userId);

        Channel channel = findChannel(request.getChannelId());

        if (!permissions.hasAccess(channel, userId)) {
            throw new ChannelAccessDeniedException("You don't have permission to access this channel");
        }
        resolveDelivery(request, channel, userId);

        // 🔥 Generate ID in producer (important for kafka mode)
        message.setId(UUID.randomUUID().toString());
        message.setTimestamp(LocalDateTime.now());
        message.setSender(user);
        message.setChannel(channel);
        message.setContent(request.getContent());



        UserDTO userDTO = UserDTO.builder()
                .avatarUrl("")
                .username(user.getUsername())
                .id(user.getId())
                .build();

        MessageResponse messageResponse = MessageResponse.builder()
                .id(message.getId()) // use generated ID
                .author(userDTO)
                .channelId(channel.getId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
        // 🔥 Delegate everything to profile-based publisher
        eventPublisher.publish(message, messageResponse, request, user);

        return message;
    }
    @Transactional(readOnly = true)
    public Page<MessageResp> getChannelMessages(Long channelId, Pageable pageable) {
        Channel channel = findChannel(channelId);
        return messageRepository.findByChannelOrderByTimestampDesc(channel, pageable)
                .map(MessageResp::fromEntity);
    }

    /** Changes only the content of an existing message; the caller has already been authorized. */
    @Transactional
    public MessageResp editMessage(String messageId, String content) {
        Message message = findMessage(messageId);
        message.setContent(content);
        message.setEdited(true);
        MessageResp response = MessageResp.fromEntity(messageRepository.save(message));
        messagingTemplate.convertAndSend(
                "/topic/channels/" + response.getChannelId() + "/messages", response);
        return response;
    }

    @Transactional
    public void deleteMessage(String messageId) {
        Message message = findMessage(messageId);
        messageRepository.delete(message);
        messagingTemplate.convertAndSend(
                "/topic/channels/" + message.getChannel().getId() + "/messages/delete",
                message.getId());
    }

    private Message findMessage(String messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", "id", messageId));
    }

    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel", "id", channelId));
    }

    /**
     * Decides who receives a message from the channel itself. The client's {@code dm} and
     * {@code receiver} fields are overwritten, so they cannot redirect a message.
     */
    private void resolveDelivery(MessageRequest request, Channel channel, Long senderId) {
        if (channel.getType() != ChannelType.DM) {
            request.setDm(false);
            request.setReceiver(null);
            return;
        }
        long recipientId = DmKey.parse(channel.getDmKey())
                .orElseThrow(() -> new ChannelAccessDeniedException("This conversation is not available"))
                .otherThan(senderId);
        User sender = findUser(senderId);
        User recipient = findUser(recipientId);
        if (isBlocked(sender, recipient) || isBlocked(recipient, sender)) {
            throw new ChannelAccessDeniedException("You cannot send messages to this user");
        }
        request.setDm(true);
        request.setReceiver(recipient.getUsername());
    }

    private boolean isBlocked(User blocker, User blocked) {
        return friendshipRepository.findBySenderAndReceiver(blocker, blocked)
                .map(friendship -> friendship.getStatus() == FriendshipStatus.BLOCKED)
                .orElse(false);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
