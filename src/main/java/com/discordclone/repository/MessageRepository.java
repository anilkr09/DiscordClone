package com.discordclone.repository;

import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, String> {
    Page<Message> findByChannelOrderByTimestampDesc(Channel channel, Pageable pageable);

    Page<Message> findByChannelOrderByTimestamp(Channel channel, Pageable pageable);
} 