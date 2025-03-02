package com.example.DiscordClone.controller;

import com.example.DiscordClone.model.Message;
import com.example.DiscordClone.service.MessageService;
import com.example.DiscordClone.kafka.KafkaProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService messageService;
    private final KafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    public MessageController(MessageService messageService, KafkaProducer kafkaProducer, ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    @GetMapping("f/history")
    public List<Message> getMessageHistory() {
        return messageService.getAllMessages();
    }

    @PostMapping("/send")
    public Message sendMessage(@RequestBody Message message) {
        Message savedMessage = messageService.saveMessage(message);
        try {
            // Send the message to Kafka
            kafkaProducer.sendMessage("chat-messages", objectMapper.writeValueAsString(savedMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return savedMessage;
    }
}
