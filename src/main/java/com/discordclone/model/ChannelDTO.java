package com.discordclone.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelDTO {
    private Long id;
    private String name;
    private String description;
    private ChannelType type;

    // We don't include the server to avoid circular references
    // We can include serverId if needed
    private Long serverId;


    public static ChannelDTO fromEntity(Channel channel) {
        if (channel == null) {
            return null;
        }

        return ChannelDTO.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .type(channel.getType())
                .serverId(channel.getServer() != null ? channel.getServer().getId() : null)

                .build();
    }

    // You might want a variant that excludes messages for listing channels
    public static ChannelDTO fromEntityWithoutMessages(Channel channel) {
        if (channel == null) {
            return null;
        }

        return ChannelDTO.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .type(channel.getType())
                .serverId(channel.getServer() != null ? channel.getServer().getId() : null)
                .build();
    }
}
