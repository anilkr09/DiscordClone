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
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private  final UserRepository userRepository;
    private final ChannelRepository channelRepository;

    @Transactional
    public Message sendMessage(MessageRequest request , Long userId ) {

        Message message = new Message();
        System.out.println("current msg " + request.toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Channel channel = channelRepository.findById(request.getChannelId())
                .orElseThrow(() -> new ResourceNotFoundException("Channel", "id", userId));


        message.setSender(user);
        message.setChannel(channel);
        message.setContent(request.getContent());

        Message savedMessage = messageRepository.save(message);
        UserDTO userDTO = UserDTO.builder()
                .avatarUrl("")
                .username(user.getUsername())
                .id(user.getId())
                .build();


        MessageResponse messageResponse = MessageResponse.builder()
                .author(userDTO)
                .id(savedMessage.getId())
                .channelId(savedMessage.getChannel().getId())
                .content(savedMessage.getContent())
                .timestamp(savedMessage.getTimestamp())
                .build();
        log.info("➡️ isDM value request {}", request.toString());

        if(request.isDm()) {
            log.info("➡️ queue msg user {}", user.getUsername());
            log.info(
                    "Sending WS message to user={} channelId={}",
                    request.getReceiver(),
                    channel.getId()
            );
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
        }
        else {
        messagingTemplate.convertAndSend(
            "/topic/channels/" + message.getChannel().getId() + "/messages",
            messageResponse
        );
        }
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