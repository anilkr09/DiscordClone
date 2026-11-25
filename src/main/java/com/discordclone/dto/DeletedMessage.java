package com.discordclone.dto;

/** Payload of a {@code MESSAGE_DELETED} event. */
public record DeletedMessage(String id, Long channelId) {
}
