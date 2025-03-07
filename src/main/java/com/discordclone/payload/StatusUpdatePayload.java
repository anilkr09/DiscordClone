package com.discordclone.payload;

import com.discordclone.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdatePayload {
    private UserStatus status;
    private String customStatusText;
    private String customStatusEmoji;
    private String expiresAt;
    
    public StatusUpdatePayload(UserStatus status) {
        this.status = status;
        this.customStatusText = null;
        this.customStatusEmoji = null;
        this.expiresAt = null;
    }
} 