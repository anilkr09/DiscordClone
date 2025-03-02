package com.example.DiscordClone.kafka;

import com.example.DiscordClone.websocket.ChatWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
    private final ChatWebSocketHandler chatWebSocketHandler;

    public KafkaConsumer(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @KafkaListener(topics = "chat-messages", groupId = "chat-group")
    public void listenMessages(String message) {
        // Broadcast the message to all connected WebSocket clients
        chatWebSocketHandler.broadcastMessage(message);
    }
}
