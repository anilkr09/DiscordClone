package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestPayload {
    
    @NotBlank(message = "Username cannot be blank")
    private String username;
} 