package com.discordclone.integration;

import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The kafka profile with no broker listening: nothing blocks and nothing is lost. */
@ActiveProfiles("kafka")
// Closed afterwards: a cached context's relay would go on draining the shared outbox.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false"
})
class KafkaOutageTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void messagesAreStoredAtOnceAndWaitInTheOutbox() throws Exception {
        User owner = newUser("owner");
        Long channelId = newChannel(newServer(owner), owner);
        List<String> ids = new ArrayList<>();

        assertTimeout(Duration.ofSeconds(1), () -> {
            for (int i = 0; i < 3; i++) {
                ids.add(messageService.sendMessage(new MessageRequest("hi", channelId), owner.getId()).getId());
            }
        });
        // Long enough for the relay to have tried and failed.
        Thread.sleep(3000);

        for (String id : ids) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM messages WHERE id = ?", Integer.class, id));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox WHERE payload LIKE ?", Integer.class, "%" + id + "%"));
        }
    }
}
