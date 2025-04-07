package com.discordclone.service;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserStatusService {
    
    /**
     * Update a user's status
     * @param userId ID of the user
     * @param status New status
     * @return Updated user
     */
    User updateUserStatus(Long userId, UserStatus status);

    @Transactional(readOnly = true)
    List<UserStatusEntity> getAllUserStatus();

    /**
     * Update a user's custom status
     * @param userId ID of the user

     * @return Updated user
     */
    User updateCustomStatus(Long userId, UserStatus customStatu);
    
    /**
     * Get a user's status
     * @param userId ID of the user
     * @return User status
     */
    UserStatus getUserStatus(Long userId);
    
    /**
     * Clear a user's custom status
     * @param userId ID of the user
     * @return Updated user
     */
    User clearCustomStatus(Long userId);
    
    /**
     * Broadcast a status change to all connected clients
     * @param userId ID of the user
     * @param status New status
     */
    void broadcastStatusChange(Long userId, UserStatus status);
} 