package com.discordclone.dto;

import com.discordclone.model.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String content;
    private UserDTO author;
    private Long channelId;
    private Instant timestamp;
    private boolean edited;
    private Instant editedAt;
    
    public static MessageDTO fromEntity(Message message) {
        return MessageDTO.builder()
            .id(message.getId())
            .content(message.getContent())
            .author(UserDTO.fromEntity(message.getSender()))
            .channelId(message.getChannel().getId())
            .timestamp(message.getTimestamp())
            .edited(message.isEdited())
            .build();
    }
}
