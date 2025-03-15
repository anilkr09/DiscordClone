package com.discordclone.service.impl;

import com.discordclone.controller.StatusWebSocketController;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.repository.UserRepository;
import com.discordclone.service.UserStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserStatusServiceImpl implements UserStatusService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger logger = LoggerFactory.getLogger(UserStatusServiceImpl.class);

    @Autowired
    public UserStatusServiceImpl(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional
    public User updateUserStatus(Long userId, UserStatus status) {
        logger.info("status updata -"+status);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        user.setStatus(status);
        User updatedUser = userRepository.save(user);
        
        // Broadcast status change
        broadcastStatusChange(userId, status);
        
        return updatedUser;
    }

    @Override
    @Transactional
    public User updateCustomStatus(Long userId, UserStatus status, String customStatusText, 
                                  String customStatusEmoji, String expiresAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        user.setStatus(status);
        // In a real implementation, you would store custom status in a separate table
        // or add fields to the User entity
        
        User updatedUser = userRepository.save(user);
        
        // Broadcast status change
        broadcastStatusChange(userId, status);
        
        return updatedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatus getUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        return user.getStatus();
    }

    @Override
    @Transactional
    public User clearCustomStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // In a real implementation, you would clear custom status fields
        
        return userRepository.save(user);
    }

    @Override
    public void broadcastStatusChange(Long userId, UserStatus status) {
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("userId", userId);
        statusUpdate.put("status", status);
        
        messagingTemplate.convertAndSend("/topic/status", statusUpdate);
    }
} 