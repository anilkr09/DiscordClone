package com.discordclone.consumer;

import com.discordclone.contract.MessageCreatedV1;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Failure handling: a record that cannot be saved is retried with exponential back-off and then
 * published to the dead-letter topic. If that publish fails too, the offset is not committed and
 * the record is delivered again, so nothing is dropped.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic messageEvents() {
        return TopicBuilder.name(MessageCreatedV1.TOPIC).partitions(MessageCreatedV1.PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic messageEventsDeadLetters() {
        return TopicBuilder.name(MessageCreatedV1.DEAD_LETTER_TOPIC)
                .partitions(MessageCreatedV1.PARTITIONS).replicas(1).build();
    }

    /** Publishes dead letters: raw bytes for records that never deserialized, JSON otherwise. */
    @Bean
    KafkaTemplate<Object, Object> deadLetterTemplate(KafkaProperties properties) {
        Map<Class<?>, Serializer<?>> values = new LinkedHashMap<>();
        values.put(byte[].class, new ByteArraySerializer());
        values.put(MessageCreatedV1.class, new JsonSerializer<MessageCreatedV1>());
        Map<Class<?>, Serializer<?>> keys = new LinkedHashMap<>();
        keys.put(byte[].class, new ByteArraySerializer());
        keys.put(String.class, new StringSerializer());

        @SuppressWarnings({"unchecked", "rawtypes"})
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                properties.buildProducerProperties(null),
                (Serializer) new DelegatingByTypeSerializer(keys),
                (Serializer) new DelegatingByTypeSerializer(values));
        return new KafkaTemplate<>(factory);
    }

    /** Spring Boot's listener container factory picks this bean up. */
    @Bean
    DefaultErrorHandler errorHandler(
            KafkaTemplate<Object, Object> deadLetterTemplate,
            @Value("${consumer.retry.max-retries:5}") int maxRetries,
            @Value("${consumer.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${consumer.retry.max-interval-ms:30000}") long maxInterval) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxInterval);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, ex) -> new TopicPartition(MessageCreatedV1.DEAD_LETTER_TOPIC, record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Retrying cannot help: the event is invalid, or its channel or author does not exist.
        handler.addNotRetryableExceptions(InvalidEventException.class, DataIntegrityViolationException.class);
        return handler;
    }
}
