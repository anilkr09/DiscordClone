package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ServerPayload {

    @NotBlank(message = "Server name is required")
    @Size(max = 100, message = "Server name can be at most 100 characters")
    private String name;

    @Size(max = 255, message = "Description can be at most 255 characters")
    private String description;
}
