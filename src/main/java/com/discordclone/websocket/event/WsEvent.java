package com.discordclone.websocket.event;

/**
 * The shape of every frame the server pushes. Increase {@link #VERSION} for a change an older
 * client could not read.
 */
public record WsEvent(WsEventType type, int version, Object payload) {

    public static final int VERSION = 1;

    public WsEvent(WsEventType type, Object payload) {
        this(type, VERSION, payload);
    }
}
