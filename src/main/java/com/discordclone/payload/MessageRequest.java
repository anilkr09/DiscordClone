package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    /** Set by the server from the channel; whatever a client sends is ignored. */
    private boolean dm;

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 4000, message = "Message cannot be longer than 4000 characters")
    private String content;

    @NotNull(message = "channelId is required")
    private Long channelId;

    /** Set by the server from the channel; whatever a client sends is ignored. */
    private String receiver;

    /** Echoed in the delivered event, so the sender can match it to the message it is showing. */
    @Size(max = 64)
    private String nonce;

    public MessageRequest(String content, Long channelId) {
        this.content = content;
        this.channelId = channelId;
    }
}
