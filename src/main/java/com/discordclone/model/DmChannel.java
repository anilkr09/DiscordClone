package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "dm_channels",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id", "channel_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One-to-one mapping with Channel
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false, unique = true)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;
}
