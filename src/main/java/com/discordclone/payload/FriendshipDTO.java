package com.discordclone.payload;

import com.discordclone.model.FriendshipStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipDTO {
    private Long id;
    private Long friendshipId;
    private FriendshipStatus status;
    private Instant lastInteraction;
    
    // Additional fields for friend requests
    private Long senderId;
    private String senderUsername;
    private String senderAvatarUrl;
    private Long receiverId;
    private String receiverUsername;
    private String receiverAvatarUrl;
    private Instant createdAt;
} 