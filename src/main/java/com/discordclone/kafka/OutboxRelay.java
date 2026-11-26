package com.discordclone.kafka;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.discordclone.contract.MessageCreatedV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes outbox rows in order and deletes each once the broker has it. A database lock lets only
 * one instance relay at a time, which preserves order. A row survives any failure and is retried,
 * so an event may be published twice but is never lost; consumers ignore repeats.
 */
@Slf4j
@Component
@Profile("kafka")
public class OutboxRelay {

    private static final long LOCK_KEY = 0x6F7574626F78L; // "outbox"
    private static final int BATCH = 100;

    private record Row(long id, String key, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<Object, Object> kafka;
    private final ObjectMapper json;
    // Its own thread: publishing can block while the broker is unreachable.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "outbox-relay"));
    private final AtomicBoolean requested = new AtomicBoolean();

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions,
                       KafkaTemplate<Object, Object> kafka, ObjectMapper json, MeterRegistry meters) {
        Gauge.builder("outbox.backlog", () -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Long.class))
                .description("Events not yet acknowledged by Kafka")
                .register(meters);
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.json = json;
    }

    @TransactionalEventListener(value = OutboxMessageEventPublisher.OutboxFilled.class,
            phase = TransactionPhase.AFTER_COMMIT)
    public void onFilled() {
        request();
    }

    /** Catches whatever an earlier attempt could not send. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}")
    public void poll() {
        request();
    }

    private void request() {
        if (!worker.isShutdown() && requested.compareAndSet(false, true)) {
            worker.execute(() -> {
                requested.set(false);
                try {
                    while (relayBatch()) {
                        // more rows are waiting
                    }
                } catch (RuntimeException ex) {
                    log.warn("Outbox relay failed and will retry: {}", ex.getMessage());
                }
            });
        }
    }

    /** @return true if a full batch was sent, so there may be more */
    private boolean relayBatch() {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
            if (!Boolean.TRUE.equals(locked)) {
                return false;
            }
            List<Row> rows = jdbc.query(
                    "SELECT id, message_key, payload FROM outbox ORDER BY id LIMIT " + BATCH,
                    (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)));
            int sent = send(rows);
            return sent == BATCH;
        }));
    }

    private int send(List<Row> rows) {
        List<Future<?>> acknowledgements = new ArrayList<>();
        try {
            for (Row row : rows) {
                acknowledgements.add(kafka.send(MessageCreatedV1.TOPIC, row.key(),
                        json.readValue(row.payload(), MessageCreatedV1.class)));
            }
        } catch (JsonProcessingException ex) {
            log.error("Unreadable outbox row", ex);
        } catch (RuntimeException ex) {
            log.warn("Kafka is not accepting events: {}", ex.getMessage());
        }
        int sent = 0;
        for (Future<?> acknowledgement : acknowledgements) {
            try {
                acknowledgement.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException ex) {
                // Stop here: later events must not overtake this one.
                log.warn("An event was not acknowledged and will be retried: {}", ex.getMessage());
                break;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            jdbc.update("DELETE FROM outbox WHERE id = ?", rows.get(sent).id());
            sent++;
        }
        return sent;
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }
}
