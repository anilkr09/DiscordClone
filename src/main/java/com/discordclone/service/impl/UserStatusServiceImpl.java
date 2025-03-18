package com.discordclone.service.impl;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.repository.UserRepository;
import com.discordclone.repository.UserStatusRepository;
import com.discordclone.service.UserStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserStatusServiceImpl implements UserStatusService {

    private final UserRepository userRepository;
    private final UserStatusRepository userStatusRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger logger = LoggerFactory.getLogger(UserStatusServiceImpl.class);

    @Autowired
    public UserStatusServiceImpl(UserRepository userRepository, 
                               UserStatusRepository userStatusRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.userStatusRepository = userStatusRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional
    public User updateUserStatus(Long userId, UserStatus status) {
        logger.info("Updating status for user {} to {}", userId, status);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        UserStatusEntity statusEntity = userStatusRepository.findById(userId.toString())
                .orElse(new UserStatusEntity());
        
//        statusEntity.setUserId(userId.toString());
        statusEntity.setCurrentStatus(status);
        userStatusRepository.save(statusEntity);
        
        // Broadcast status change
        broadcastStatusChange(userId, status);
        
        return user;
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserStatusEntity> getAllUserStatus() {
        return userStatusRepository.findAll();
    }

    @Override
    @Transactional
    public User updateCustomStatus(Long userId, UserStatus currentStatus, UserStatus customStatus, String expiresAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        UserStatusEntity statusEntity = userStatusRepository.findById(userId.toString())
                .orElse(new UserStatusEntity());
        
//        statusEntity.setUserId(userId.toString());
        statusEntity.setCurrentStatus(currentStatus);
        statusEntity.setCustomStatus(customStatus);
        
        if (expiresAt != null && !expiresAt.isEmpty()) {
            LocalDateTime expiryTime = LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_DATE_TIME);
            statusEntity.setStatusExpiresAt(expiryTime);
        }
        
        userStatusRepository.save(statusEntity);
        
        // Broadcast status change

        broadcastStatusChange(userId, (customStatus!=null)?customStatus:currentStatus);
        
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatus getUserStatus(Long userId) {
        return userStatusRepository.findById(userId.toString())
                .map(UserStatusEntity::getCurrentStatus)
                .orElse(UserStatus.OFFLINE);
    }

    @Override
    @Transactional
    public User clearCustomStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        UserStatusEntity statusEntity = userStatusRepository.findById(userId.toString())
                .orElse(new UserStatusEntity());
        
        statusEntity.setCustomStatus(null);
        statusEntity.setStatusExpiresAt(null);
        userStatusRepository.save(statusEntity);
        
        return user;
    }

    @Override
    public void broadcastStatusChange(Long userId, UserStatus status) {
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("userId", userId);
        statusUpdate.put("status", status);
        
        messagingTemplate.convertAndSend("/topic/status", statusUpdate);
    }
} 