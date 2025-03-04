package com.discordclone.service;

import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Message sendMessage(Message message) {
        Message savedMessage = messageRepository.save(message);
        messagingTemplate.convertAndSend(
            "/topic/channels/" + message.getChannel().getId() + "/messages",
            savedMessage
        );
        return savedMessage;
    }

    @Transactional(readOnly = true)
    public Page<Message> getChannelMessages(Channel channel, Pageable pageable) {
        return messageRepository.findByChannelOrderByTimestampDesc(channel, pageable);
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