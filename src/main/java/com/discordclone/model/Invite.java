package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invite {
    @Id
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    private int maxUses;
    private int uses;
    private LocalDateTime expiry;
}
