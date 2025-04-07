package com.discordclone.payload;

import com.discordclone.model.UserStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // ✅ Ignore unknown fields to avoid errors
public class StatusUpdatePayload {

    @JsonProperty("currentStatus")
    private String currentStatus;

    @JsonProperty("customStatus")
    private String customStatus;

    @JsonProperty("expiresAt")
    private String expiresAt;

    @JsonProperty("lastActivity")
    private String lastActivity;

    @JsonProperty("lastStatusChange")
    private String lastStatusChange;

    // ✅ Use only ONE constructor with @JsonCreator to prevent conflicts
    @JsonCreator
    public StatusUpdatePayload(
            @JsonProperty("currentStatus") String currentStatus,
            @JsonProperty("customStatus") String customStatus,
            @JsonProperty("expiresAt") String expiresAt,
            @JsonProperty("lastActivity") String lastActivity,
            @JsonProperty("lastStatusChange") String lastStatusChange) {
        this.currentStatus = currentStatus;
        this.customStatus = customStatus;
        this.expiresAt = expiresAt;
        this.lastActivity = lastActivity;
        this.lastStatusChange = lastStatusChange;
    }
}
