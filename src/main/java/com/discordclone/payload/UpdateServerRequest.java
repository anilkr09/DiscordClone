package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateServerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description) {
}
