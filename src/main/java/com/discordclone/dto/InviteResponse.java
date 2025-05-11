package com.discordclone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InviteResponse {
    private String inviteUrl;
    private String code;
    private Long serverId;
    private int maxUses;
    private String expiresAt;
}
