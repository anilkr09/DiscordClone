package com.discordclone.service;

import com.discordclone.model.Friendship;
import com.discordclone.model.User;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendResponsePayload;

import java.util.List;

public interface FriendshipService {
    
    /**
     * Send a friend request to another user
     * @param senderId ID of the user sending the request
     * @param request Friend request payload containing the username of the receiver
     * @return The created friendship
     */
    Friendship sendFriendRequest(Long senderId, FriendRequestPayload request);
    
    /**
     * Accept a friend request
     * @param requestId ID of the friend request
     * @param userId ID of the user accepting the request
     * @return The updated friendship
     */
    Friendship acceptFriendRequest(Long requestId, Long userId);
    
    /**
     * Reject a friend request
     * @param requestId ID of the friend request
     * @param userId ID of the user rejecting the request
     */
    void rejectFriendRequest(Long requestId, Long userId);
    
    /**
     * Remove a friendship
     * @param userId ID of the user removing the friendship
     * @param friendId ID of the friend to remove
     */
    void removeFriend(Long userId, Long friendId);
    
    /**
     * Block a user
     * @param userId ID of the user blocking
     * @param targetId ID of the user to block
     * @return The created or updated friendship
     */
    Friendship blockUser(Long userId, Long targetId);
    
    /**
     * Unblock a user
     * @param userId ID of the user unblocking
     * @param targetId ID of the user to unblock
     */
    void unblockUser(Long userId, Long targetId);
    
    /**
     * Get all friends of a user
     * @param userId ID of the user
     * @return List of friend response payloads
     */
    List<FriendResponsePayload> getFriends(Long userId);
    
    /**
     * Get all pending friend requests for a user
     * @param userId ID of the user
     * @return List of friend response payloads
     */
    List<FriendResponsePayload> getPendingRequests(Long userId);
    
    /**
     * Check if two users are friends
     * @param userId ID of the first user
     * @param friendId ID of the second user
     * @return true if they are friends, false otherwise
     */
    boolean areFriends(Long userId, Long friendId);
} 