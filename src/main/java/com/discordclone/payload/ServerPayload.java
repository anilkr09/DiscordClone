package com.discordclone.payload;

import com.discordclone.model.ServerType;
import lombok.Data;

@Data

public class ServerPayload {

    String name;
    String description;
    ServerType type;

}
