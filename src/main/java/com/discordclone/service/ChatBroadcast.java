package com.discordclone.service;

/**
 * Something to push to subscribers once the current transaction has committed, so that no client is
 * told about a change that is then rolled back.
 *
 * @param username recipient of a user destination, or null for a topic
 */
public record ChatBroadcast(String username, String destination, Object payload) {

    public static ChatBroadcast toTopic(String destination, Object payload) {
        return new ChatBroadcast(null, destination, payload);
    }

    public static ChatBroadcast toUser(String username, String destination, Object payload) {
        return new ChatBroadcast(username, destination, payload);
    }
}
