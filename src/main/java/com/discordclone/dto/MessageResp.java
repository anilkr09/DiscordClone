package com.discordclone.dto;

import com.discordclone.model.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor  // <--- Add this for Jackson
@Getter

public class MessageResp {
    private Long id;
    private String content;
    private Long channelId;
    private Long senderId;
    private Author author; // optional, helpful for display
    private LocalDateTime timestamp;
    private boolean edited;



    public static MessageResp fromEntity(Message message) {
        return new MessageResp(
                message.getId(),
                message.getContent(),
                message.getChannel().getId(),
                message.getSender().getId(),
                new Author(message.getSender().getId(),message.getSender().getUsername(),message.getSender().getEmail()),
                message.getTimestamp(),
                message.isEdited()
        );
    }


}
@AllArgsConstructor
@NoArgsConstructor
@Getter

class Author{
    private Long id;
    private String username;
    private String email;
}