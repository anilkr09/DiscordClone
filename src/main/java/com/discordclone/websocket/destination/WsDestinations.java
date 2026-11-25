package com.discordclone.websocket.destination;

/** Every destination the server pushes to. Subscribing is authorized in {@code StompSecurityConfig}. */
public final class WsDestinations {
    public static final String FRIENDS = "/queue/friends";
    public static final String MESSAGES = "/queue/messages";
    public static final String PRESENCE = "/queue/presence";
    public static final String ERRORS = "/queue/errors";

    private WsDestinations() {
    }

    public static String channelMessages(Long channelId) {
        return "/topic/channels/" + channelId + "/messages";
    }

    public static String serverChannels(Long serverId) {
        return "/topic/servers/" + serverId + "/channels";
    }
}
