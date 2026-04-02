package com.discordclone.service;

import com.discordclone.payload.MessageResponse;

public interface MessageEventPublisher {
    void publish(MessageResponse message);
}