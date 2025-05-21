package com.discordclone.model;

import com.discordclone.model.*;
import com.discordclone.payload.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerDTO {
    private Long id;
    private String name;
    private String description;
    private UserDTO owner;
    private Set<ChannelDTO> channels;

    // Constructors, getters, setters

    public static ServerDTO fromEntity(Server server) {
        ServerDTO dto = new ServerDTO();
        dto.setId(server.getId());
        dto.setName(server.getName());
        dto.setDescription(server.getDescription());
        dto.setOwner(UserDTO.fromEntity(server.getOwner()));

        dto.setChannels(server.getChannels().stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toSet()));
        return dto;
    }

    // Additional DTO classes for User, Member, Channel
}