package com.discordclone.payload;

import com.discordclone.model.FriendshipStatus;
import com.discordclone.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponsePayload {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private UserStatus status;
    private Long friendshipId;
    private FriendshipStatus friendshipStatus;
    private LocalDateTime lastInteraction;
    
    // Additional fields for friend requests
    private Long senderId;
    private String senderUsername;
    private String senderAvatarUrl;
    private Long receiverId;
    private String receiverUsername;
    private String receiverAvatarUrl;
    private LocalDateTime createdAt;
} 