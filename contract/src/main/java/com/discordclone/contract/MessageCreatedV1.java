package com.discordclone.contract;

import java.time.Instant;
import java.util.List;

/**
 * A chat message that has been accepted. Changes must stay readable by the previous consumer: add
 * optional fields only, and create a new version for anything else.
 *
 * @param id         application-generated UUID; the consumer's idempotency key
 * @param channelId  channel the message belongs to
 * @param content    message text
 * @param timestamp  when the application accepted the message
 * @param author     sender
 * @param nonce      optional; the sending client's own reference
 * @param recipients optional; for a direct message, the usernames to deliver it to. Absent for a
 *                   server channel, whose subscribers receive it
 */
public record MessageCreatedV1(String id, Long channelId, String content, Instant timestamp, Author author,
                               String nonce, List<String> recipients) {

    public static final String TOPIC = "message-events";
    public static final String DEAD_LETTER_TOPIC = TOPIC + "-dlt";
    public static final int PARTITIONS = 4;

    public record Author(Long id, String username) {
    }
}
