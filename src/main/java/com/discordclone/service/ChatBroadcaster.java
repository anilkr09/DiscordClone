package com.discordclone.service;

import com.discordclone.cluster.ClusterBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatBroadcaster {

    private final ClusterBus bus;

    // fallbackExecution: an event published outside a transaction is sent straight away.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(ChatBroadcast broadcast) {
        if (broadcast.username() == null) {
            bus.toTopic(broadcast.destination(), broadcast.payload());
        } else {
            bus.toUser(broadcast.username(), broadcast.destination(), broadcast.payload());
        }
    }
}
