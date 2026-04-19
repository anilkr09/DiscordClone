package com.discordclone.constants;


public class PresenceKeys {

    public static String lastSeen(Long userId) {
        return "presence:last_seen:" + userId;
    }

    public static String lastActivity(Long userId) {
        return "presence:last_activity:" + userId;
    }

    public static String custom(Long userId) {
        return "presence:custom:" + userId;
    }

    public static String status(Long userId) {
        return "presence:status:" + userId;
    }
}