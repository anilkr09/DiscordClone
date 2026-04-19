package com.discordclone.service;

import com.discordclone.model.Friendship;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendDTO;
import com.discordclone.payload.FriendshipDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FriendshipService {
    
    /**
     * Send a friend request to another user
     * @param senderId ID of the user sending the request
     * @param request Friend request payload containing the username of the receiver
     * @return The created friendship
     */
    FriendshipDTO sendFriendRequest(Long senderId, FriendRequestPayload request);
    
    /**
     * Accept a friend request
     * @param requestId ID of the friend request
     * @param userId ID of the user accepting the request
     * @return The updated friendship
     */
    FriendDTO acceptFriendRequest(Long requestId, Long userId);
    
    /**
     * Reject a friend request
     * @param requestId ID of the friend request
     * @param userId ID of the user rejecting the request
     */
    FriendshipDTO rejectFriendRequest(Long requestId, Long userId);
    
    /**
     * Remove a friendship
     * @param userId ID of the user removing the friendship
     * @param friendId ID of the friend to remove
     */
    FriendDTO removeFriend(Long userId, Long friendId);
    
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
    List<FriendDTO> getFriends(Long userId);
    
    /**
     * Get all pending friend requests for a user
     * @param userId ID of the user
     * @return List of friend response payloads
     */
    List<FriendshipDTO> getPendingRequests(Long userId);

    @Transactional(readOnly = true)
    List<FriendshipDTO> getOutgoingRequests(Long userId);

    /**
     * Check if two users are friends
     * @param userId ID of the first user
     * @param friendId ID of the second user
     * @return true if they are friends, false otherwise
     */
    boolean areFriends(Long userId, Long friendId);

    List<Long> getFriendIds(Long userId);
} 