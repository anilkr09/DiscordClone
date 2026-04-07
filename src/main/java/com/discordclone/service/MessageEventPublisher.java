package com.discordclone.service;

import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;

public interface MessageEventPublisher {
    void publish(Message message, MessageResponse response, MessageRequest request, User user);
}