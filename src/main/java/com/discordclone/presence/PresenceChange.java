package com.discordclone.presence;

import com.discordclone.model.UserStatus;

/** Payload of a {@code PRESENCE_CHANGED} event. */
public record PresenceChange(Long userId, UserStatus status) {
}
