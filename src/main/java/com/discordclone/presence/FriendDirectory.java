package com.discordclone.presence;

import com.discordclone.repository.FriendshipRepository;
import com.discordclone.repository.UserRepository;
import com.discordclone.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** Who may hear about a user: their accepted friends, by username, cached in Redis. */
@Component
@RequiredArgsConstructor
public class FriendDirectory {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    // Distinguishes "no friends" from "not cached"; usernames cannot contain spaces.
    private static final String NOBODY = " ";

    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final FriendshipRepository friendships;

    @Transactional(readOnly = true)
    public Set<String> friendNames(Long userId) {
        String key = key(userId);
        Set<String> cached = redis.opsForSet().members(key);
        if (cached == null || cached.isEmpty()) {
            cached = load(userId);
            Set<String> stored = new HashSet<>(cached);
            stored.add(NOBODY);
            redis.opsForSet().add(key, stored.toArray(String[]::new));
            redis.expire(key, CACHE_TTL);
        }
        Set<String> names = new HashSet<>(cached);
        names.remove(NOBODY);
        return names;
    }

    public void invalidate(Long userId) {
        redis.delete(key(userId));
    }

    private Set<String> load(Long userId) {
        Set<String> names = new HashSet<>();
        users.findById(userId).ifPresent(user -> friendships.findAcceptedFriendships(user).forEach(friendship -> {
            User friend = friendship.getSender().getId().equals(userId)
                    ? friendship.getReceiver() : friendship.getSender();
            names.add(friend.getUsername());
        }));
        return names;
    }

    private static String key(Long userId) {
        return "friends:" + userId;
    }
}
