package com.discordclone.service;

import com.discordclone.payload.MessageResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class NoOpMessageEventPublisher implements MessageEventPublisher {

    @Override
    public void publish(MessageResponse message) {
        // do nothing
    }
}