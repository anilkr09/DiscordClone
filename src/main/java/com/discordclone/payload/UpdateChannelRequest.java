package com.discordclone.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deliberately has no type: changing it would change how the channel is authorized. */
public record UpdateChannelRequest(
        @NotBlank @Size(min = 2, max = 50) String name,
        @Size(max = 200) String description) {
}
