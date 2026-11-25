package com.discordclone.presence;

import com.discordclone.model.UserStatus;

import java.time.Instant;

/**
 * What is known about one user's presence. Any field may be null.
 *
 * @param lastHeartbeat when a client last showed it was connected
 * @param lastActivity  when the user last did something
 * @param custom        the status the user chose, valid until {@code customUntil}
 */
public record PresenceState(Instant lastHeartbeat, Instant lastActivity, UserStatus custom, Instant customUntil) {

    /**
     * Liveness is checked first: a user who is not connected is offline whatever they chose. Then
     * the chosen status applies, and otherwise recent activity separates online from idle.
     */
    public UserStatus resolve(Instant now, PresenceProperties properties) {
        if (lastHeartbeat == null || !now.isBefore(lastHeartbeat.plus(properties.heartbeatTtl()))) {
            return UserStatus.OFFLINE;
        }
        if (custom != null && customUntil != null && now.isBefore(customUntil)) {
            return custom;
        }
        if (lastActivity != null && now.isBefore(lastActivity.plus(properties.onlineWindow()))) {
            return UserStatus.ONLINE;
        }
        return UserStatus.IDLE;
    }
}
