package com.discordclone.cluster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.discordclone.security.StompSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reaches sockets wherever they are connected. The STOMP broker is local to each instance, so a
 * push is published to Redis and every instance, including the sender, delivers it to its own
 * sockets. Sign-outs travel the same way, so that every instance closes the login's sockets.
 */
@Slf4j
@Component
public class ClusterBus implements MessageListener {

    static final String CHANNEL = "cluster:events";

    /**
     * @param username    recipient of a user destination, or null for a topic
     * @param closedLogin set instead of the other fields when a login has ended
     */
    record Envelope(String username, String destination, JsonNode payload, String closedLogin) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SimpMessagingTemplate messaging;
    private final StompSessionRegistry sockets;

    public ClusterBus(StringRedisTemplate redis, ObjectMapper json, SimpMessagingTemplate messaging,
                      StompSessionRegistry sockets) {
        this.redis = redis;
        this.json = json;
        this.messaging = messaging;
        this.sockets = sockets;
    }

    public void toTopic(String destination, Object payload) {
        publish(new Envelope(null, destination, json.valueToTree(payload), null));
    }

    public void toUser(String username, String destination, Object payload) {
        publish(new Envelope(username, destination, json.valueToTree(payload), null));
    }

    public void closeLogin(String loginSessionId) {
        publish(new Envelope(null, null, null, loginSessionId));
    }

    private void publish(Envelope envelope) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize an event", ex);
        } catch (DataAccessException ex) {
            // Better to reach this instance's sockets than nobody's.
            log.warn("Redis is unavailable; delivering to this instance only: {}", ex.getMessage());
            deliver(envelope);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliver(json.readValue(message.getBody(), Envelope.class));
        } catch (IOException ex) {
            log.error("Unreadable cluster event", ex);
        }
    }

    private void deliver(Envelope envelope) {
        if (envelope.closedLogin() != null) {
            sockets.closeLogin(envelope.closedLogin());
        } else if (envelope.username() == null) {
            messaging.convertAndSend(envelope.destination(), envelope.payload());
        } else {
            messaging.convertAndSendToUser(envelope.username(), envelope.destination(), envelope.payload());
        }
    }
}
