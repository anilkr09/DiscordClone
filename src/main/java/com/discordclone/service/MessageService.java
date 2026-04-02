package com.discordclone.service;

import com.discordclone.dto.MessageResp;
import com.discordclone.exception.ResourceNotFoundException;
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
    @Transactional
    public Message sendMessage(MessageRequest request, Long userId) {

        Message message = new Message();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Channel channel = channelRepository.findById(request.getChannelId())
                .orElseThrow(() -> new ResourceNotFoundException("Channel", "id", userId));

        // 🔥 Generate ID in producer (important for kafka mode)
        message.setId(UUID.randomUUID().toString());
        message.setTimestamp(LocalDateTime.now());
        message.setSender(user);
        message.setChannel(channel);
        message.setContent(request.getContent());

        // ✅ DB save only in LOCAL profile
        Message savedMessage = persistenceService.save(message);

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

        if (request.isDm()) {
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/messages",
                    messageResponse
            );

            messagingTemplate.convertAndSendToUser(
                    request.getReceiver(),
                    "/queue/messages",
                    messageResponse
            );
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + channel.getId() + "/messages",
                    messageResponse
            );
        }

        //  2. Persistance only
        eventPublisher.publish(messageResponse);

        return savedMessage;
    }
    @Transactional(readOnly = true)
    public Page<Message> getChannelMessages(Channel channel, Pageable pageable) {

        Page<Message> messages =
                messageRepository.findByChannelOrderByTimestampDesc(channel, pageable);



        return messages;
    }
    @Transactional(readOnly = true)
    public Page<MessageResp> getChannelMessages1(Channel channel, Pageable pageable) {

        Page<Message> messages =
                messageRepository.findByChannelOrderByTimestampDesc(channel, pageable);

        Page<MessageResp> response =
                messages.map(MessageResp::fromEntity);

        return response;
    }


    @Transactional
    public Message editMessage(Message message) {
        Message savedMessage = messageRepository.save(message);
        messagingTemplate.convertAndSend(
            "/topic/channels/" + message.getChannel().getId() + "/messages",
            savedMessage
        );
        return savedMessage;
    }

    @Transactional
    public void deleteMessage(Message message) {
        messageRepository.delete(message);
        messagingTemplate.convertAndSend(
            "/topic/channels/" + message.getChannel().getId() + "/messages/delete",
            message.getId()
        );
    }
} 