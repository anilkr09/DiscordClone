package com.discordclone.integration;

import com.discordclone.contract.MessageCreatedV1;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Outbox to Kafka to fan-out, against a real broker. */
@ActiveProfiles("kafka")
// Closed afterwards: a cached context's relay would go on draining the shared outbox.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaPipelineTest extends IntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    JdbcTemplate jdbc;
    @SpyBean
    SimpMessagingTemplate messaging;

    @Test
    void aMessageIsStoredPublishedInOrderAndDeliveredByFanOut() {
        User owner = newUser("owner");
        Long channelId = newChannel(newServer(owner), owner);
        // The fan-out consumer starts at the end of the topic, so wait for it to be listening.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            messageService.sendMessage(new MessageRequest("warm-up", channelId), owner.getId());
            verify(messaging, timeout(500).atLeastOnce())
                    .convertAndSend(eq("/topic/channels/" + channelId + "/messages"), any(Object.class));
        });

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(newMessage(channelId, owner));
        }

        await().atMost(Duration.ofSeconds(30)).until(() ->
                jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class) == 0);
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "reader-" + unique(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            reader.subscribe(List.of(MessageCreatedV1.TOPIC));
            List<String> published = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                reader.poll(Duration.ofMillis(500)).forEach(record -> ids.stream()
                        .filter(id -> record.value().contains(id) && !published.contains(id))
                        .forEach(published::add));
                return published.size() == ids.size();
            });
            assertEquals(ids, published);
        }
    }
}
