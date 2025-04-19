package com.discordclone.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
    @NoArgsConstructor
    @AllArgsConstructor
@Builder

    public class MessageResponse {
        private Long id;
        private String content;
        private UserDTO author;
        private Long channelId;
        private LocalDateTime timestamp; // or LocalDateTime, depending on your use
    }

