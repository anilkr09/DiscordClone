package com.discordclone.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    // fallbackExecution: an event published outside a transaction is sent straight away.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(ChatBroadcast broadcast) {
        if (broadcast.username() == null) {
            messagingTemplate.convertAndSend(broadcast.destination(), broadcast.payload());
        } else {
            messagingTemplate.convertAndSendToUser(
                    broadcast.username(), broadcast.destination(), broadcast.payload());
        }
    }
}
