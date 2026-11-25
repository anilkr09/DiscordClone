package com.discordclone.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The participants encoded in a DM channel's key, {@code dm-{minId}-{maxId}}.
 */
public record DmKey(long first, long second) {

    private static final Pattern FORMAT = Pattern.compile("dm-(\\d{1,18})-(\\d{1,18})");

    public static String of(long userId1, long userId2) {
        return "dm-" + Math.min(userId1, userId2) + "-" + Math.max(userId1, userId2);
    }

    public static Optional<DmKey> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(key);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new DmKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))));
    }

    public boolean includes(Long userId) {
        return userId != null && (userId == first || userId == second);
    }

    /** The participant other than {@code userId}; for a DM with oneself, the same user. */
    public long otherThan(long userId) {
        return userId == first ? second : first;
    }
}
