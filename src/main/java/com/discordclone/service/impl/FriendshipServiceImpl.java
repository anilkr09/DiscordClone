package com.discordclone.service.impl;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.Friendship;
import com.discordclone.model.FriendshipStatus;
import com.discordclone.model.User;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendResponsePayload;
import com.discordclone.repository.FriendshipRepository;
import com.discordclone.repository.UserRepository;
import com.discordclone.service.FriendshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    @Autowired
    public FriendshipServiceImpl(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Friendship sendFriendRequest(Long senderId, FriendRequestPayload request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", senderId));
        
        User receiver = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));
        
        // Check if the user is trying to add themselves
        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("You cannot add yourself as a friend");
        }
        
        // Check if a friendship already exists
        Optional<Friendship> existingFriendship = friendshipRepository.findBySenderAndReceiver(sender, receiver);
        if (existingFriendship.isPresent()) {
            Friendship friendship = existingFriendship.get();
            if (friendship.getStatus() == FriendshipStatus.PENDING) {
                throw new IllegalArgumentException("Friend request already sent");
            } else if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new IllegalArgumentException("You are already friends with this user");
            } else if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
                throw new AccessDeniedException("You cannot send a friend request to this user");
            }
            
            // If rejected, update to pending
            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setUpdatedAt(LocalDateTime.now());
            return friendshipRepository.save(friendship);
        }
        
        // Check if there's a request from the receiver to the sender
        Optional<Friendship> reverseRequest = friendshipRepository.findBySenderAndReceiver(receiver, sender);
        if (reverseRequest.isPresent()) {
            Friendship friendship = reverseRequest.get();
              if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                  throw new IllegalArgumentException("You are already friends with this user");
              }
            else if (friendship.getStatus() == FriendshipStatus.PENDING) {
                // Auto-accept the existing request
                friendship.setStatus(FriendshipStatus.ACCEPTED);
                friendship.setUpdatedAt(LocalDateTime.now());
                return friendshipRepository.save(friendship);
            } else if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
                throw new AccessDeniedException("You cannot send a friend request to this user");
            }
        }
        
        // Create a new friendship
        Friendship friendship = new Friendship();
        friendship.setSender(sender);
        friendship.setReceiver(receiver);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship.setCreatedAt(LocalDateTime.now());
        friendship.setUpdatedAt(LocalDateTime.now());
        
        return friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public Friendship acceptFriendRequest(Long requestId, Long userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request", "id", requestId));
        
        // Verify the user is the receiver of the request
        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new AccessDeniedException("You cannot accept this friend request");
        }
        
        // Verify the request is pending
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException("This friend request cannot be accepted");
        }
        
        // Accept the request
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        
        return friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public void rejectFriendRequest(Long requestId, Long userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request", "id", requestId));
        
        // Verify the user is the receiver of the request
        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new AccessDeniedException("You cannot reject this friend request");
        }
        
        // Verify the request is pending
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException("This friend request cannot be rejected");
        }
        
        // Reject the request
        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        
        friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", friendId));
        
        // Find the friendship
        Optional<Friendship> friendship1 = friendshipRepository.findBySenderAndReceiver(user, friend);
        Optional<Friendship> friendship2 = friendshipRepository.findBySenderAndReceiver(friend, user);
        
        Friendship friendship = friendship1.orElseGet(() -> friendship2.orElseThrow(
                () -> new ResourceNotFoundException("Friendship", "users", userId + " and " + friendId)));
        
        // Verify they are friends
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalArgumentException("You are not friends with this user");
        }
        
        // Remove the friendship
        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public Friendship blockUser(Long userId, Long targetId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetId));
        
        // Check if a friendship already exists
        Optional<Friendship> existingFriendship = friendshipRepository.findBySenderAndReceiver(user, target);
        Optional<Friendship> reverseFriendship = friendshipRepository.findBySenderAndReceiver(target, user);
        
        Friendship friendship;
        
        if (existingFriendship.isPresent()) {
            friendship = existingFriendship.get();
        } else if (reverseFriendship.isPresent()) {
            // If there's a reverse friendship, delete it and create a new one
            friendshipRepository.delete(reverseFriendship.get());
            
            friendship = new Friendship();
            friendship.setSender(user);
            friendship.setReceiver(target);
            friendship.setCreatedAt(LocalDateTime.now());
        } else {
            // Create a new friendship
            friendship = new Friendship();
            friendship.setSender(user);
            friendship.setReceiver(target);
            friendship.setCreatedAt(LocalDateTime.now());
        }
        
        // Set status to blocked
        friendship.setStatus(FriendshipStatus.BLOCKED);
        friendship.setUpdatedAt(LocalDateTime.now());
        
        return friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public void unblockUser(Long userId, Long targetId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetId));
        
        // Find the block
        Optional<Friendship> friendship = friendshipRepository.findBySenderAndReceiver(user, target);
        
        if (friendship.isPresent() && friendship.get().getStatus() == FriendshipStatus.BLOCKED) {
            // Remove the block
            friendshipRepository.delete(friendship.get());
        } else {
            throw new ResourceNotFoundException("Block", "target", targetId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponsePayload> getFriends(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        List<Friendship> friendships = friendshipRepository.findAcceptedFriendships(user);
        
        return friendships.stream()
                .map(friendship -> {
                    User friend = friendship.getSender().getId().equals(userId) 
                            ? friendship.getReceiver() : friendship.getSender();
                    
                    FriendResponsePayload response = new FriendResponsePayload();
                    response.setId(friend.getId());
                    response.setUsername(friend.getUsername());
                    response.setEmail(friend.getEmail());
                    response.setFriendshipId(friendship.getId());
                    response.setFriendshipStatus(friendship.getStatus());
                    response.setLastInteraction(friendship.getUpdatedAt());
                    
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponsePayload> getPendingRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Get incoming requests
        List<Friendship> incomingRequests = friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING);
        
        // Get outgoing requests
        List<Friendship> outgoingRequests = friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.PENDING);
        
        List<FriendResponsePayload> result = new ArrayList<>();
        
        // Map incoming requests
        incomingRequests.forEach(friendship -> {
            FriendResponsePayload response = new FriendResponsePayload();
            response.setId(friendship.getId());
            response.setSenderId(friendship.getSender().getId());
            response.setSenderUsername(friendship.getSender().getUsername());
            response.setReceiverId(friendship.getReceiver().getId());
            response.setReceiverUsername(friendship.getReceiver().getUsername());
            response.setFriendshipStatus(friendship.getStatus());
            response.setCreatedAt(friendship.getCreatedAt());
            
            result.add(response);
        });
        
        // Map outgoing requests
        outgoingRequests.forEach(friendship -> {
            FriendResponsePayload response = new FriendResponsePayload();
            response.setId(friendship.getId());
            response.setSenderId(friendship.getSender().getId());
            response.setSenderUsername(friendship.getSender().getUsername());
            response.setReceiverId(friendship.getReceiver().getId());
            response.setReceiverUsername(friendship.getReceiver().getUsername());
            response.setFriendshipStatus(friendship.getStatus());
            response.setCreatedAt(friendship.getCreatedAt());
            
            result.add(response);
        });
        
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", friendId));
        
        return friendshipRepository.existsFriendship(user, friend);
    }
} 