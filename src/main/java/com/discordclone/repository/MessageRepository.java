package com.discordclone.repository;

import com.discordclone.model.Message;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {

    @Query("""
            SELECT m FROM Message m JOIN FETCH m.sender
            WHERE m.channel.id = :channelId
            ORDER BY m.timestamp DESC, m.id DESC""")
    List<Message> findLatest(Long channelId, Limit limit);

    @Query("""
            SELECT m FROM Message m JOIN FETCH m.sender
            WHERE m.channel.id = :channelId
              AND (m.timestamp < :timestamp OR (m.timestamp = :timestamp AND m.id < :id))
            ORDER BY m.timestamp DESC, m.id DESC""")
    List<Message> findOlderThan(Long channelId, Instant timestamp, String id, Limit limit);
}
