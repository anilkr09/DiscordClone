package com.discordclone.dto;

import com.discordclone.model.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private String content;
    private UserDTO author;
    private Long channelId;
    private LocalDateTime timestamp;
    private boolean edited;
    private LocalDateTime editedAt;
    
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
