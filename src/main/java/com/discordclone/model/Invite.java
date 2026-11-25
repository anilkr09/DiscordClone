package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invite {
    @Id
    private String code;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    private int maxUses;
    private int uses;
    private Instant expiry;

    /** Identity is the database key, so equality never touches lazy associations. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Invite that && code != null && code.equals(that.getCode());
    }

    @Override
    public int hashCode() {
        return Invite.class.hashCode();
    }
}
