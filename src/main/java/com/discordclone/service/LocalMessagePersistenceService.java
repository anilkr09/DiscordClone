package com.discordclone.service;

import com.discordclone.model.Message;
import com.discordclone.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalMessagePersistenceService implements MessagePersistenceService {

    private final MessageRepository messageRepository;

    @Override
    public Message save(Message message) {
        return messageRepository.save(message);
    }
}