package com.discordclone.service;

import com.discordclone.model.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
public class NoOpMessagePersistenceService implements MessagePersistenceService {

    @Override
    public Message save(Message message) {
        return message;
    }
}