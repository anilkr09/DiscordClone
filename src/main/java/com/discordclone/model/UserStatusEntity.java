package com.discordclone.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private UserStatus currentStatus = UserStatus.OFFLINE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "custom_status")
    private UserStatus customStatus;
    
    @Column(name = "status_expires_at")
    private LocalDateTime statusExpiresAt;
    
    @Column(name = "last_activity")
    private LocalDateTime lastActivity;
    
    @Column(name = "last_status_change")
    private LocalDateTime lastStatusChange;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", insertable = false, updatable = false)
    private User user;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (lastStatusChange == null) {
            lastStatusChange = LocalDateTime.now();
        }
        lastActivity = LocalDateTime.now();
    }
} 