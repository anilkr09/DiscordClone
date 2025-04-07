package com.discordclone.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserStatus {
    ONLINE, OFFLINE, IDLE, DO_NOT_DISTURB;

    @JsonCreator
    public static UserStatus fromString(String value) {
        return UserStatus.valueOf(value.toUpperCase()); // ✅ Convert safely
    }

    @JsonValue
    public String toJson() {
        return this.name();
    }
}
