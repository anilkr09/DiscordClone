package com.discordclone.websocket.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class WsEvent {
    private WsEventType type;
    private Object payload;
}