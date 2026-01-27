package com.discordclone.websocket.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WsEvent {
    private WsEventType type;
    private Object payload;
}