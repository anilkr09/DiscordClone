package com.discordclone.presence;

import com.discordclone.model.UserStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Presence in Redis.
 * <ul>
 * <li>{@code presence:{id}}: hash of {@code hb}, {@code act}, {@code custom}, {@code until} and
 *     {@code status}, the value last announced</li>
 * <li>{@code presence:alive}: sorted set of connected users, scored by last heartbeat</li>
 * <li>{@code presence:sessions:{id}}: the user's open sockets, scored by each one's deadline</li>
 * <li>{@code presence:leaving}: users whose last socket closed, scored by when the grace period ends</li>
 * </ul>
 * Anything that decides a transition runs as a script, so it happens exactly once however many
 * instances are running.
 */
@Component
public class PresenceStore {

    static final String ALIVE = "presence:alive";
    static final String LEAVING = "presence:leaving";

    // Removes the closed socket and, if none is left, starts the grace period.
    private static final RedisScript<Long> DISCONNECT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[1]) == 0 then
              redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
              return 1
            end
            return 0
            """, Long.class);

    // Returns the users who have just gone: silent for too long, or past the grace period with no socket.
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SWEEP = new DefaultRedisScript<>("""
            local now, silent_before = ARGV[1], ARGV[2]
            local gone = {}
            local function leave(id)
              if redis.call('ZREM', KEYS[1], id) == 1 then
                redis.call('HDEL', 'presence:' .. id, 'hb')
                table.insert(gone, id)
              end
            end
            for _, id in ipairs(redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', silent_before, 'LIMIT', 0, 500)) do
              leave(id)
            end
            for _, id in ipairs(redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, 500)) do
              redis.call('ZREM', KEYS[2], id)
              local sessions = 'presence:sessions:' .. id
              redis.call('ZREMRANGEBYSCORE', sessions, '-inf', now)
              if redis.call('ZCARD', sessions) == 0 then
                leave(id)
              end
            end
            return gone
            """, List.class);

    // 1 if the announced status changes, so that one caller announces each change.
    private static final RedisScript<Long> ANNOUNCE = new DefaultRedisScript<>("""
            local previous = redis.call('HGET', KEYS[1], 'status') or 'OFFLINE'
            if previous == ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'status', ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final PresenceProperties properties;

    public PresenceStore(StringRedisTemplate redis, PresenceProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    private static String user(Long userId) {
        return "presence:" + userId;
    }

    private static String sessions(Long userId) {
        return "presence:sessions:" + userId;
    }

    public PresenceState read(Long userId) {
        Map<Object, Object> hash = redis.opsForHash().entries(user(userId));
        String custom = (String) hash.get("custom");
        return new PresenceState(instant(hash.get("hb")), instant(hash.get("act")),
                custom == null ? null : UserStatus.valueOf(custom), instant(hash.get("until")));
    }

    public void heartbeat(Long userId, Instant now) {
        redis.opsForHash().put(user(userId), "hb", millis(now));
        redis.opsForZSet().add(ALIVE, userId.toString(), now.toEpochMilli());
        keep(userId);
    }

    public void activity(Long userId, Instant now) {
        redis.opsForHash().put(user(userId), "act", millis(now));
    }

    /** Records an open socket, and cancels any departure that was pending. */
    public void connected(Long userId, String sessionId, Instant now) {
        redis.opsForZSet().add(sessions(userId), sessionId,
                now.plus(properties.heartbeatTtl()).toEpochMilli());
        redis.expire(sessions(userId), properties.heartbeatTtl());
        redis.opsForZSet().remove(LEAVING, userId.toString());
    }

    public void disconnected(Long userId, String sessionId, Instant now) {
        redis.execute(DISCONNECT, List.of(sessions(userId), LEAVING), sessionId, millis(now),
                millis(now.plus(properties.disconnectGrace())), userId.toString());
    }

    @SuppressWarnings("unchecked")
    public List<Long> sweep(Instant now) {
        List<String> gone = redis.execute(SWEEP, List.of(ALIVE, LEAVING), millis(now),
                millis(now.minus(properties.heartbeatTtl())));
        return gone == null ? List.of() : gone.stream().map(Long::valueOf).toList();
    }

    /** @return true if {@code status} differs from what was last announced, and is now recorded */
    public boolean announce(Long userId, UserStatus status) {
        Long changed = redis.execute(ANNOUNCE, List.of(user(userId)), status.name());
        keep(userId);
        return changed != null && changed == 1;
    }

    public void setCustom(Long userId, UserStatus status, Instant until) {
        redis.opsForHash().putAll(user(userId), Map.of("custom", status.name(), "until", millis(until)));
        keep(userId);
    }

    public void clearCustom(Long userId) {
        redis.opsForHash().delete(user(userId), "custom", "until");
    }

    /** @return true the first time it is asked since Redis last lost this user's data */
    public boolean markLoaded(Long userId) {
        return redis.opsForHash().putIfAbsent(user(userId), "loaded", "1");
    }

    public void forget(Long userId) {
        redis.delete(List.of(user(userId), sessions(userId)));
        redis.opsForZSet().remove(ALIVE, userId.toString());
        redis.opsForZSet().remove(LEAVING, userId.toString());
    }

    // Housekeeping only: removes the hash of someone not seen since a chosen status would have lapsed.
    private void keep(Long userId) {
        redis.expire(user(userId), properties.customTtl().plus(Duration.ofHours(1)));
    }

    private static String millis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static Instant instant(Object millis) {
        return millis == null ? null : Instant.ofEpochMilli(Long.parseLong((String) millis));
    }
}
