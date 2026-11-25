package com.discordclone.consumer;

import com.discordclone.contract.MessageCreatedV1;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;

@Repository
public class MessageStore {

    // One statement, safe to replay: the producer's UUID is the idempotency key.
    private static final String INSERT = """
            INSERT INTO messages (id, content, channel_id, user_id, timestamp, edited)
            VALUES (?, ?, ?, ?, ?, FALSE)
            ON CONFLICT (id) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public MessageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(MessageCreatedV1 event) {
        if (event.id() == null || event.channelId() == null || event.content() == null
                || event.timestamp() == null || event.author() == null || event.author().id() == null) {
            throw new InvalidEventException("Message event is missing a required field: id=" + event.id());
        }
        jdbc.update(INSERT, event.id(), event.content(), event.channelId(), event.author().id(),
                event.timestamp().atOffset(ZoneOffset.UTC));
    }
}
