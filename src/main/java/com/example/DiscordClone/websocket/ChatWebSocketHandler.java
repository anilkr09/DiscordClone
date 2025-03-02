package com.example.DiscordClone.websocket;

import com.example.DiscordClone.kafka.KafkaProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final KafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(KafkaProducer kafkaProducer, ObjectMapper objectMapper) {
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Send message to Kafka
        kafkaProducer.sendMessage("chat-messages", message.getPayload());
    }

    public void broadcastMessage(String message) {
        sessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}


