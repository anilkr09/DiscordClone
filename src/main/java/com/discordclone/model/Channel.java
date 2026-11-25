package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(
        name = "channels",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"server_id", "name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // optional for DM

    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChannelType type = ChannelType.TEXT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = true) // ✅ nullable for DM
    private Server server;

    @Column(unique = true)
    private String dmKey; // ✅ ONLY for DM

    /** Identity is the database key, so equality never touches lazy associations. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Channel that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Channel.class.hashCode();
    }
}
