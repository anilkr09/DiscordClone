package com.discordclone.model;

import com.discordclone.dto.MessageDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    // Include messages if needed, but be careful about potential size
    // Typically you might want to load messages separately or paginate them
    @Builder.Default
    private List<MessageDTO> messages = new ArrayList<>();

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
