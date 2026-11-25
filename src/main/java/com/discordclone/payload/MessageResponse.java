package com.discordclone.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
    @NoArgsConstructor
    @AllArgsConstructor
@Builder

    public class MessageResponse {
        private String id;
        private String content;
        private UserDTO author;
        private Long channelId;
        private Instant timestamp; // or Instant, depending on your use
    }

