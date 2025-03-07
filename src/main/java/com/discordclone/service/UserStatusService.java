package com.discordclone.service;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;

public interface UserStatusService {
    
    /**
     * Update a user's status
     * @param userId ID of the user
     * @param status New status
     * @return Updated user
     */
    User updateUserStatus(Long userId, UserStatus status);
    
    /**
     * Update a user's custom status
     * @param userId ID of the user
     * @param status New status
     * @param customStatusText Custom status text
     * @param customStatusEmoji Custom status emoji
     * @param expiresAt Expiration time
     * @return Updated user
     */
    User updateCustomStatus(Long userId, UserStatus status, String customStatusText, 
                           String customStatusEmoji, String expiresAt);
    
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