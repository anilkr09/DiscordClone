package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

    @Data
    public class ChannelPayload {

        @NotBlank(message = "Channel name is required")
        @Size(min = 2, max = 50, message = "Channel name must be between 2 and 50 characters")
        private String name;

        @Size(max = 200, message = "Description can be at most 200 characters")
        private String description;
    }

