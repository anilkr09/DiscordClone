package com.discordclone.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Reported in health but deliberately left out of readiness: the outbox keeps the application
 * useful through a broker outage, so one must not take every instance out of service.
 */
@Component("kafka")
@Profile("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_MS = 2000;

    private final KafkaAdmin admin;

    public KafkaHealthIndicator(KafkaAdmin admin) {
        this.admin = admin;
    }

    @Override
    public Health health() {
        try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
            int nodes = client.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS))
                    .nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            return Health.up().withDetail("nodes", nodes).build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down(ex).build();
        } catch (ExecutionException | java.util.concurrent.TimeoutException | RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
