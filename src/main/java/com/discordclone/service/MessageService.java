package com.discordclone.service;

import com.discordclone.dto.MessagePage;
import com.discordclone.dto.MessageResp;
import com.discordclone.repository.ChannelMemberRepository;
import com.discordclone.exception.ForbiddenException;
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

import org.springframework.data.domain.Limit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher events;
    private  final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final MessagePersistenceService persistenceService;
    private final MessageEventPublisher eventPublisher;
    private final FriendshipRepository friendshipRepository;
    private final PermissionService permissions;
    private final ChannelMemberRepository channelMemberRepository;
    @Transactional
    public Message sendMessage(MessageRequest request, Long userId) {

        Message message = new Message();

        User user = findUser(userId);

        Channel channel = findChannel(request.getChannelId());

        if (!permissions.hasAccess(channel, userId)) {
            throw new ForbiddenException("You don't have permission to access this channel");
        }
        resolveDelivery(request, channel, userId);

        // 🔥 Generate ID in producer (important for kafka mode)
        message.setId(UUID.randomUUID().toString());
        // Microseconds are what the database keeps, so cursors compare equal after a round trip.
        message.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));
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
    /**
     * One page of history, newest first. {@code before} is the ID of the oldest message the caller
     * already has; paging by cursor stays correct while new messages arrive.
     */
    @Transactional(readOnly = true)
    public MessagePage getChannelMessages(Long channelId, String before, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        // One extra row tells whether anything older remains.
        Limit fetch = Limit.of(size + 1);
        List<Message> rows;
        if (before == null) {
            rows = messageRepository.findLatest(channelId, fetch);
        } else {
            Message cursor = findMessage(before);
            if (!cursor.getChannel().getId().equals(channelId)) {
                throw new ResourceNotFoundException("Message", "id", before);
            }
            rows = messageRepository.findOlderThan(channelId, cursor.getTimestamp(), cursor.getId(), fetch);
        }
        List<MessageResp> content = rows.stream().limit(size).map(MessageResp::fromEntity).toList();
        return new MessagePage(content, rows.size() > size);
    }

    /** Changes only the content of an existing message; the caller has already been authorized. */
    @Transactional
    public MessageResp editMessage(String messageId, String content) {
        Message message = findMessage(messageId);
        message.setContent(content);
        message.setEdited(true);
        MessageResp response = MessageResp.fromEntity(messageRepository.save(message));
        events.publishEvent(ChatBroadcast.toTopic(
                "/topic/channels/" + response.getChannelId() + "/messages", response));
        return response;
    }

    @Transactional
    public void deleteMessage(String messageId) {
        Message message = findMessage(messageId);
        messageRepository.delete(message);
        events.publishEvent(ChatBroadcast.toTopic(
                "/topic/channels/" + message.getChannel().getId() + "/messages/delete",
                message.getId()));
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
        // The other participant; in a conversation with oneself, the sender.
        long recipientId = channelMemberRepository.findUserIds(channel.getId()).stream()
                .filter(id -> !id.equals(senderId))
                .findFirst()
                .orElse(senderId);
        User sender = findUser(senderId);
        User recipient = findUser(recipientId);
        if (isBlocked(sender, recipient) || isBlocked(recipient, sender)) {
            throw new ForbiddenException("You cannot send messages to this user");
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
