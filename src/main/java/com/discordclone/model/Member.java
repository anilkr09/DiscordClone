package com.discordclone.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "server_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(MemberId.class)
@Builder
public class Member {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "server_id")
    private Long serverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", insertable = false, updatable = false)
    private Server server;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "joined_at")
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    // Getters and Setters
}
