package com.discordclone.presence;

/** Two users became, or stopped being, friends. */
public record FriendshipChanged(Long firstUserId, Long secondUserId) {
}
