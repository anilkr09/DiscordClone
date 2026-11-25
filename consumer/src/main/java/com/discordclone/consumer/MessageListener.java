package com.discordclone.consumer;

import com.discordclone.contract.MessageCreatedV1;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MessageListener {

    private final MessageStore store;

    public MessageListener(MessageStore store) {
        this.store = store;
    }

    /** The offset is committed only after this returns; failures go to the error handler. */
    @KafkaListener(topics = MessageCreatedV1.TOPIC)
    public void consume(MessageCreatedV1 event) {
        store.save(event);
    }
}
