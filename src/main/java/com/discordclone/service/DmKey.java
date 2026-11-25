package com.discordclone.service;

/** The unique key that keeps one DM channel per pair of users. Access is decided by channel_members. */
public final class DmKey {

    private DmKey() {
    }

    public static String of(long userId1, long userId2) {
        return "dm-" + Math.min(userId1, userId2) + "-" + Math.max(userId1, userId2);
    }
}
