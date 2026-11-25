package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_status")
@Getter
@Setter
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
    private Instant statusExpiresAt;

    @Column(name = "last_activity")
    private Instant lastActivity;

    /** Identity is the database key, so equality never touches lazy associations. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof UserStatusEntity that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return UserStatusEntity.class.hashCode();
    }
}
