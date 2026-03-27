package com.discordclone.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "server_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @EmbeddedId
    private MemberId id;

    // 🔗 USER RELATION
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    // 🔗 SERVER RELATION
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("serverId")
    @JoinColumn(name = "server_id", nullable = false)
    @JsonBackReference
    private Server server;

    // 🧾 EXTRA FIELDS
    @Column(name = "nickname", length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();
}