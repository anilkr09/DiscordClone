package com.discordclone.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(
        name = "channels",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"server_id", "name"})
        }
)
@Data
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
}