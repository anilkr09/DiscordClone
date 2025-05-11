package com.discordclone.service;


import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.service.impl.UserStatusServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
        import java.util.stream.Collectors;

@Service
public class RedisUserStatusService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String PREFIX = "user_status:";
    private static final Logger logger = LoggerFactory.getLogger(RedisUserStatusService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    public RedisUserStatusService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Set or update a user's status
    public void setUserStatus(Long userId, String status) {
        redisTemplate.opsForValue().set(PREFIX + userId, status);
    }

    // Get status of a specific user
    public String getUserStatus(Long userId) {
        return redisTemplate.opsForValue().get(PREFIX + userId);
    }

    // Get status of all users
    public Map<Long, String> getAllUserStatuses() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null || keys.isEmpty()) return Collections.emptyMap();

        List<String> statuses = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, String> result = new HashMap<>();
        int count=0;
        int i = 0;
        for (String key : keys) {
            String userIdStr = key.replace(PREFIX, "");
            Long userId = Long.valueOf(userIdStr);
            result.put(userId, statuses.get(i));
            i++;
        }

        return result;
    }

    // Optional: Remove status (e.g. on logout)
    public void removeUserStatus(Long userId) {
        redisTemplate.delete(PREFIX + userId);
    }
    public void updateUserStatus(Long userId, UserStatus status) {


        setUserStatus(userId,status.toString());
        broadcastStatusChange(userId,status);
    }
        public void broadcastStatusChange(Long userId, UserStatus status) {
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("userId", userId);
        statusUpdate.put("status", status);
        logger.info("broadcast msg {}",statusUpdate);
        logger.info("broadcast msg inside status service{}",statusUpdate);


        messagingTemplate.convertAndSend("/topic/status", statusUpdate);
    }
}
