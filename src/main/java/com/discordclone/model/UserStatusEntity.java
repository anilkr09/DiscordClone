package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_status")
    private UserStatus customStatus;

    @Column(name = "status_expires_at")
    private LocalDateTime statusExpiresAt;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;
}