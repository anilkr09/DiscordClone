package com.discordclone.integration;

import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** The kafka profile with no broker listening: senders must not be held up, and must be told. */
@ActiveProfiles("kafka")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false"
})
class KafkaOutageTest extends IntegrationTest {

    @SpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void sendingReturnsAtOnceAndTheSenderLearnsOfTheFailure() {
        User owner = newUser("owner");
        Long channelId = newChannel(newServer(owner), owner);

        // Several sends in a row: none may wait for the broker on the calling thread.
        assertTimeout(Duration.ofSeconds(1), () -> {
            for (int i = 0; i < 3; i++) {
                messageService.sendMessage(new MessageRequest(false, "hi", channelId, null), owner.getId());
            }
        });

        verify(messagingTemplate, timeout(3000).atLeastOnce())
                .convertAndSendToUser(eq(owner.getUsername()), eq("/queue/errors"), any());
    }
}
