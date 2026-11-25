package com.discordclone.websocket.event;

/** The client's types are generated from this enum, through the OpenAPI contract. */
public enum WsEventType {
    MESSAGE_CREATED,
    MESSAGE_UPDATED,
    MESSAGE_DELETED,
    PRESENCE_CHANGED,
    FRIEND_REQUEST_RECEIVED,
    FRIEND_ACCEPTED,
    FRIEND_REJECTED,
    FRIEND_REMOVED,
    CHANNEL_CREATED,
    CHANNEL_UPDATED,
    CHANNEL_DELETED,
    ERROR
}
