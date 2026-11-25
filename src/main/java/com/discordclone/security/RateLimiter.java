package com.discordclone.security;

import com.discordclone.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Token buckets in Redis, so that a limit holds across instances. */
@Slf4j
@Component
public class RateLimiter {

    public enum Action {
        LOGIN, LOGIN_ADDRESS, REGISTER, REFRESH, INVITE_JOIN, CHAT
    }

    private static final RedisScript<Long> TAKE = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local per_ms = capacity / tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(state[1]) or capacity
            local at = tonumber(state[2]) or now
            tokens = math.min(capacity, tokens + math.max(0, now - at) * per_ms)
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', now)
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]) + 1000)
            return allowed
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** @throws TooManyRequestsException if {@code subject} has used up its allowance for the action */
    public void check(Action action, Object subject) {
        RateLimitProperties.Limit limit = properties.limits() == null ? null : properties.limits().get(action);
        if (!properties.enabled() || limit == null) {
            return;
        }
        String key = "rate:" + action.name().toLowerCase(Locale.ROOT) + ":" + subject;
        try {
            Long allowed = redis.execute(TAKE, List.of(key),
                    String.valueOf(limit.capacity()), String.valueOf(limit.period().toMillis()));
            if (allowed != null && allowed == 0) {
                throw new TooManyRequestsException("Too many requests. Try again shortly.");
            }
        } catch (DataAccessException ex) {
            // An unreachable limiter must not take sign-in and chat down with it.
            log.warn("Rate limit not applied: {}", ex.getMessage());
        }
    }
}
