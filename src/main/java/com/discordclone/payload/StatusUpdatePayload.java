package com.discordclone.payload;

import com.discordclone.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdatePayload {
    private UserStatus currentStatus;
    private UserStatus customStatus;
    private String expiresAt;
    private String lastActivity;
    private String lastStatusChange;
    
    public StatusUpdatePayload(UserStatus currentStatus) {
        this.currentStatus = currentStatus;
    }
    
    public StatusUpdatePayload(UserStatus currentStatus, UserStatus customStatus, String expiresAt) {
        this.currentStatus = currentStatus;
        this.customStatus = customStatus;
        this.expiresAt = expiresAt;
    }
} 