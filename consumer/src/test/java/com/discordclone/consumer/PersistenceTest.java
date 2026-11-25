package com.discordclone.consumer;

import com.discordclone.contract.MessageCreatedV1;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ConsumerApplication.class, properties = {
        "spring.config.name=consumer",
        "consumer.retry.max-retries=2",
        "consumer.retry.initial-interval-ms=200",
        "consumer.retry.max-interval-ms=400",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.data-source-properties.socketTimeout=2",
        "spring.sql.init.mode=always",
        "spring.flyway.locations=filesystem:../src/main/resources/db/migration",
        "spring.sql.init.data-locations=classpath:data-test.sql",
        "spring.jpa.defer-datasource-initialization=true"
})
class PersistenceTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));
    static KafkaConsumer<String, byte[]> deadLetters;

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    private static final List<ConsumerRecord<String, byte[]>> DEAD = new ArrayList<>();

    @BeforeAll
    static void watchDeadLetters() {
        deadLetters = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-watcher",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new ByteArrayDeserializer());
        deadLetters.subscribe(List.of(MessageCreatedV1.DEAD_LETTER_TOPIC));
    }

    @AfterAll
    static void close() {
        deadLetters.close();
    }

    private static String event(String id, long channelId) {
        return """
                {"id":"%s","channelId":%d,"content":"hi","timestamp":"2026-01-01T10:00:00Z",\
                "author":{"id":1,"username":"a"}}""".formatted(id, channelId);
    }

    private static void publish(String... payloads) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>(MessageCreatedV1.TOPIC, "1", payload));
            }
        }
    }

    private long rows(List<String> ids) {
        return ids.stream().filter(id -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE id = ?", Long.class, id) == 1).count();
    }

    private static long deadLettered(List<String> ids) {
        deadLetters.poll(Duration.ofMillis(500)).forEach(DEAD::add);
        return ids.stream().filter(id -> DEAD.stream()
                .anyMatch(r -> new String(r.value(), StandardCharsets.UTF_8).contains(id))).count();
    }

    @Test
    void storesAMessageOnceEvenWhenDeliveredTwice() {
        String id = UUID.randomUUID().toString();
        String marker = UUID.randomUUID().toString();

        publish(event(id, 1), event(id, 1), event(marker, 1));

        await().atMost(Duration.ofSeconds(30)).until(() -> rows(List.of(id, marker)) == 2);
        assertEquals(0, deadLettered(List.of(id)));
    }

    @Test
    void deadLettersEventsThatCanNeverBeStoredAndCarriesOn() {
        String unknownChannel = UUID.randomUUID().toString();
        String missingAuthor = UUID.randomUUID().toString();
        String malformed = "not-json-" + UUID.randomUUID();
        String good = UUID.randomUUID().toString();

        publish(event(unknownChannel, 999), "{\"id\":\"" + missingAuthor + "\",\"channelId\":1}",
                malformed, event(good, 1));

        await().atMost(Duration.ofSeconds(30)).until(() -> rows(List.of(good)) == 1
                && deadLettered(List.of(unknownChannel, missingAuthor, malformed)) == 3);
    }

    @Test
    void losesNothingWhileTheDatabaseIsUnavailable() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        var docker = POSTGRES.getDockerClient();

        docker.pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            publish(ids.stream().map(id -> event(id, 1)).toArray(String[]::new));
            // Long enough for at least one record to exhaust its retries.
            await().atMost(Duration.ofSeconds(60)).until(() -> deadLettered(ids) >= 1);
        } finally {
            docker.unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(90)).until(() -> rows(ids) + deadLettered(ids) >= ids.size());
        assertTrue(ids.stream().allMatch(id -> rows(List.of(id)) == 1 || deadLettered(List.of(id)) == 1));
    }
}
