package com.discordclone.payload;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class FriendResponse {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
}
