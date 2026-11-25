package com.discordclone.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "servers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String name;

    private String description;


//    @Enumerated(EnumType.STRING)
//    @Builder.Default
//    private ServerType type= ServerType.PUBLIC;
    @Enumerated(EnumType.STRING)
    private ServerType type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Identity is the database key, so equality never touches lazy associations. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Server that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Server.class.hashCode();
    }
}
