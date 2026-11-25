package com.discordclone.kafka;

import com.discordclone.contract.MessageCreatedV1;
import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import com.discordclone.service.MessageEventPublisher;
import com.discordclone.service.MessagePersistenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

/**
 * Stores the message and the event describing it in one transaction, so that neither can exist
 * without the other. {@link OutboxRelay} publishes the event after the commit, and every instance's
 * {@link MessageFanOut} delivers it.
 */
@Service
@Profile("kafka")
@RequiredArgsConstructor
public class OutboxMessageEventPublisher implements MessageEventPublisher {

    /** Tells the relay that there is something to send. */
    public record OutboxFilled() {
    }

    private final MessagePersistenceService persistenceService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    @Override
    public void publish(Message message, MessageResponse response, MessageRequest request, User user) {
        persistenceService.save(message);

        List<String> recipients = request.isDm()
                ? Stream.of(user.getUsername(), request.getReceiver()).distinct().toList()
                : null;
        MessageCreatedV1 event = new MessageCreatedV1(response.getId(), response.getChannelId(),
                response.getContent(), response.getTimestamp(),
                new MessageCreatedV1.Author(response.getAuthor().getId(), user.getUsername()),
                response.getNonce(), recipients);
        try {
            // Keyed by channel, so that one channel's messages stay in order.
            jdbc.update("INSERT INTO outbox (message_key, payload) VALUES (?, ?)",
                    event.channelId().toString(), json.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize a message event", ex);
        }
        events.publishEvent(new OutboxFilled());
    }
}
