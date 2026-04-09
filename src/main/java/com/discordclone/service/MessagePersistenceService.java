package com.discordclone.service;

import com.discordclone.model.Message;

public interface MessagePersistenceService {
    Message save(Message message);
}
