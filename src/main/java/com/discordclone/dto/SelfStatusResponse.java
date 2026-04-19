package com.discordclone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SelfStatusResponse {
    private String status;        // ONLINE / IDLE / OFFLINE
    private String customStatus;  // DND / INVISIBLE / null
    private String expiresAt;
}