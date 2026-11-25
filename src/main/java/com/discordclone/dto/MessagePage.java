package com.discordclone.dto;

import java.util.List;

/** A page of message history, newest first. */
public record MessagePage(List<MessageResp> content, boolean hasMore) {
}
