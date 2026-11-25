
package com.discordclone.payload;

import com.discordclone.model.FriendshipStatus;
import com.discordclone.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {
    private Long userId;
    private String status;

}