package com.discordclone.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A database created by Hibernate before migrations existed is brought up to date with its data. */
@Testcontainers
class LegacyMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/migration/V1__baseline.sql"),
                    "/docker-entrypoint-initdb.d/1-legacy-schema.sql");

    @Test
    void existingDataSurvivesTheMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        // The dump leaves search_path alone, so tables are in the public schema as in a real database.
        jdbc.execute("INSERT INTO users (id, username, email, password) VALUES "
                + "(1,'a','a@x','p'), (2,'b','b@x','p')");
        jdbc.execute("INSERT INTO servers (id, name, owner_id) VALUES (1, 'Default Server', 1)");
        jdbc.execute("INSERT INTO channels (id, type, dm_key, server_id) VALUES (10, 'DM', 'dm-1-2', 1)");
        jdbc.execute("INSERT INTO messages (id, content, edited, \"timestamp\", channel_id, user_id) "
                + "VALUES ('m', 'hi', false, '2026-01-01 10:00:00', 10, 1)");
        jdbc.execute("INSERT INTO friendships (id, created_at, status, updated_at, sender_id, receiver_id) VALUES "
                + "(1, now(), 'PENDING', now(), 1, 2), (2, now(), 'ACCEPTED', now(), 2, 1)");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true).baselineVersion("1")
                .load().migrate();

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM channel_members WHERE channel_id = 10", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM channels WHERE id = 10 AND server_id IS NULL", Integer.class));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT status FROM friendships", String.class));
        assertEquals("2026-01-01 10:00:00+00", jdbc.queryForObject(
                "SELECT (\"timestamp\" AT TIME ZONE 'UTC')::text || '+00' FROM messages", String.class));
    }
}
