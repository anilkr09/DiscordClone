package com.discordclone.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "channels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @ManyToOne()
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;
    @Builder.Default
    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL)
    @JsonManagedReference

    private List<Message> messages = new ArrayList<>();
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ChannelType type = ChannelType.TEXT;
} 