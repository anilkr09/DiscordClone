package com.discordclone.presence;

import com.discordclone.model.UserStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The status state machine at its boundaries. */
class PresenceStateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final PresenceProperties PROPERTIES = new PresenceProperties(Duration.ofSeconds(30),
            Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofHours(24));

    private static Instant ago(Integer seconds) {
        return seconds == null ? null : NOW.minusSeconds(seconds);
    }

    @ParameterizedTest(name = "heartbeat {0}s ago, activity {1}s ago, chose {2} -> {3}")
    @CsvSource(nullValues = "-", textBlock = """
            -,   -,   -,              OFFLINE
            0,   0,   -,              ONLINE
            0,   29,  -,              ONLINE
            0,   31,  -,              IDLE
            0,   299, -,              IDLE
            0,   301, -,              IDLE
            0,   -,   -,              IDLE
            29,  0,   -,              ONLINE
            30,  0,   -,              OFFLINE
            31,  0,   -,              OFFLINE
            0,   0,   DO_NOT_DISTURB, DO_NOT_DISTURB
            0,   301, IDLE,           IDLE
            0,   0,   OFFLINE,        OFFLINE
            31,  0,   DO_NOT_DISTURB, OFFLINE
            -,   -,   DO_NOT_DISTURB, OFFLINE
            """)
    void resolves(Integer heartbeat, Integer activity, UserStatus chosen, UserStatus expected) {
        PresenceState state = new PresenceState(ago(heartbeat), ago(activity), chosen, NOW.plusSeconds(60));

        assertEquals(expected, state.resolve(NOW, PROPERTIES));
    }

    @ParameterizedTest
    @CsvSource({"1, DO_NOT_DISTURB", "0, ONLINE", "-1, ONLINE"})
    void aChosenStatusLapses(int secondsLeft, UserStatus expected) {
        PresenceState state = new PresenceState(NOW, NOW, UserStatus.DO_NOT_DISTURB, NOW.plusSeconds(secondsLeft));

        assertEquals(expected, state.resolve(NOW, PROPERTIES));
    }
}
