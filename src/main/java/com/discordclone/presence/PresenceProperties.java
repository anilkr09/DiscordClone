package com.discordclone.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param heartbeatTtl     silence after which a user counts as gone
 * @param onlineWindow     how recently a user must have been active to show as online rather than idle
 * @param activityThrottle activity reports arriving closer together than this are ignored
 * @param disconnectGrace  wait after the last socket closes, so that a page reload shows nothing
 * @param customTtl        how long a chosen status lasts
 */
@ConfigurationProperties("app.presence")
public record PresenceProperties(
        @DefaultValue("30s") Duration heartbeatTtl,
        @DefaultValue("30s") Duration onlineWindow,
        @DefaultValue("5s") Duration activityThrottle,
        @DefaultValue("8s") Duration disconnectGrace,
        @DefaultValue("24h") Duration customTtl) {
}
