package com.discordclone.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Server {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    private ServerType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonManagedReference
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Builder.Default
    @JsonManagedReference
    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Member> members = new HashSet<>();
    @JsonManagedReference
    @Builder.Default
    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL)
    private Set<Channel> channels = new HashSet<>();
}
