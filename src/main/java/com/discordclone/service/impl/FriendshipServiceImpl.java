package com.discordclone.service.impl;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.exception.ConflictException;
import com.discordclone.model.*;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.payload.FriendDTO;
import com.discordclone.payload.FriendshipDTO;
import com.discordclone.repository.*;
import com.discordclone.service.FriendshipService;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import com.discordclone.websocket.publisher.WebSocketPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketPublisher webSocketPublisher;
    @Autowired
    public FriendshipServiceImpl(
            FriendshipRepository friendshipRepository,
            SimpMessagingTemplate messagingTemplate,
            UserRepository userRepository,
            WebSocketPublisher webSocketPublisher
    ) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.webSocketPublisher = webSocketPublisher;
    }

    @Override
    @Transactional
    public FriendshipDTO sendFriendRequest(Long senderId, FriendRequestPayload request) {

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", senderId));

        User receiver = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));

        if (sender.getId().equals(receiver.getId())) {
            throw new ConflictException("You cannot add yourself as a friend");
        }

        Friendship friendship;

        // Check existing request (sender → receiver)
        Optional<Friendship> existingFriendship =
                friendshipRepository.findBySenderAndReceiver(sender, receiver);

        if (existingFriendship.isPresent()) {
            friendship = existingFriendship.get();

            switch (friendship.getStatus()) {
                case PENDING:
                    throw new ConflictException("Friend request already sent");
                case ACCEPTED:
                    throw new ConflictException("You are already friends with this user");
                case BLOCKED:
                    throw new ConflictException("You cannot send a friend request to this user");
            }

            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setUpdatedAt(LocalDateTime.now());
            friendship = friendshipRepository.save(friendship);
        }
        else {
            // Check reverse request (receiver → sender)
            Optional<Friendship> reverseRequest =
                    friendshipRepository.findBySenderAndReceiver(receiver, sender);

            if (reverseRequest.isPresent()) {
                friendship = reverseRequest.get();

                if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                    throw new ConflictException("You are already friends with this user");
                } else if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
                    throw new ConflictException("You cannot send a friend request to this user");
                }

                // Auto-accept reverse pending request
                friendship.setStatus(FriendshipStatus.ACCEPTED);
                friendship.setUpdatedAt(LocalDateTime.now());
                friendship = friendshipRepository.save(friendship);
            }
            else {
                friendship = new Friendship();
                friendship.setSender(sender);
                friendship.setReceiver(receiver);
                friendship.setStatus(FriendshipStatus.PENDING);
                friendship.setCreatedAt(LocalDateTime.now());
                friendship.setUpdatedAt(LocalDateTime.now());

                friendship = friendshipRepository.save(friendship);
            }

        }

        FriendshipDTO response = new FriendshipDTO();
        response.setId(friendship.getId());
        response.setSenderId(friendship.getSender().getId());
        response.setSenderUsername(friendship.getSender().getUsername());
        response.setReceiverId(friendship.getReceiver().getId());
        response.setReceiverUsername(friendship.getReceiver().getUsername());
        response.setStatus(friendship.getStatus());
        response.setCreatedAt(friendship.getCreatedAt());
        webSocketPublisher.sendToUser(response.getReceiverUsername(), WsDestinations.FRIENDS,new WsEvent(
                WsEventType.FRIEND_REQUEST_RECEIVED,  Map.of("request",response)
        ));
        return response;
    }

    @Override
    @Transactional
    public FriendDTO acceptFriendRequest(Long requestId, Long userId) {

        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request", "id", requestId));

        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new ConflictException("You cannot accept this friend request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("This friend request cannot be accepted");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());

        friendshipRepository.save(friendship);

        // DTO for API response (receiver sees sender as friend)
        FriendDTO responseDto = FriendDTO.builder()
                .id(friendship.getSender().getId())
                .username(friendship.getSender().getUsername())
                .email(friendship.getSender().getEmail())
                .build();

        // DTO for WebSocket notification (sender sees receiver as friend)
        FriendDTO senderNotificationDto = FriendDTO.builder()
                .id(friendship.getReceiver().getId())
                .username(friendship.getReceiver().getUsername())
                .email(friendship.getReceiver().getEmail())
                .build();



        webSocketPublisher.sendToUser(friendship.getSender().getUsername(), WsDestinations.FRIENDS,new WsEvent(
                WsEventType.FRIEND_ACCEPTED,  Map.of("friend",senderNotificationDto,"requestId",requestId)
        ));

        return responseDto;
    }

    @Override
    @Transactional
    public FriendshipDTO rejectFriendRequest(Long requestId, Long userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request", "id", requestId));

        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new ConflictException("You cannot reject this friend request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("This friend request cannot be rejected");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setUpdatedAt(LocalDateTime.now());

        friendshipRepository.save(friendship);

        FriendshipDTO response = new FriendshipDTO();
        response.setId(friendship.getId());
        response.setSenderId(friendship.getSender().getId());
        response.setSenderUsername(friendship.getSender().getUsername());
        response.setReceiverId(friendship.getReceiver().getId());
        response.setReceiverUsername(friendship.getReceiver().getUsername());
        response.setStatus(friendship.getStatus());
        response.setCreatedAt(friendship.getCreatedAt());
        webSocketPublisher.sendToUser(friendship.getSender().getUsername(), WsDestinations.FRIENDS,
        new WsEvent(
                WsEventType.FRIEND_REJECTED,  Map.of("id",requestId)
        ));
        return response;
    }

    @Override
    @Transactional
    public FriendDTO removeFriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", friendId));
        FriendDTO response = FriendDTO.builder()
                .id(friend.getId())
                .username(friend.getUsername())
                .email(friend.getEmail())
                .build();
        Optional<Friendship> friendship1 = friendshipRepository.findBySenderAndReceiver(user, friend);
        Optional<Friendship> friendship2 = friendshipRepository.findBySenderAndReceiver(friend, user);

        Friendship friendship = friendship1.orElseGet(() ->
                friendship2.orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Friendship", "users", userId + " and " + friendId)));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ConflictException("You are not friends with this user");
        }
        friendshipRepository.delete(friendship);

        webSocketPublisher.sendToUser(friend.getUsername(), WsDestinations.FRIENDS,
      new WsEvent(
                WsEventType.FRIEND_REMOVED,  Map.of("friendId",userId)
        ));
        return response;
    }

    @Override
    @Transactional
    public Friendship blockUser(Long userId, Long targetId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetId));

        Optional<Friendship> existingFriendship = friendshipRepository.findBySenderAndReceiver(user, target);
        Optional<Friendship> reverseFriendship = friendshipRepository.findBySenderAndReceiver(target, user);

        Friendship friendship;

        if (existingFriendship.isPresent()) {
            friendship = existingFriendship.get();
        } else if (reverseFriendship.isPresent()) {
            friendshipRepository.delete(reverseFriendship.get());
            friendship = new Friendship();
            friendship.setSender(user);
            friendship.setReceiver(target);
            friendship.setCreatedAt(LocalDateTime.now());
        } else {
            friendship = new Friendship();
            friendship.setSender(user);
            friendship.setReceiver(target);
            friendship.setCreatedAt(LocalDateTime.now());
        }

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

        Optional<Friendship> friendship =
                friendshipRepository.findBySenderAndReceiver(user, target);

        if (friendship.isPresent() &&
                friendship.get().getStatus() == FriendshipStatus.BLOCKED) {
            friendshipRepository.delete(friendship.get());
        } else {
            throw new ResourceNotFoundException("Block", "target", targetId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendDTO> getFriends(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<Friendship> friendships =
                friendshipRepository.findAcceptedFriendships(user);

        return friendships.stream()
                .map(friendship -> {
                    User friend =
                            friendship.getSender().getId().equals(userId)
                                    ? friendship.getReceiver()
                                    : friendship.getSender();

                    FriendDTO response = new FriendDTO();
                    response.setId(friend.getId());
                    response.setUsername(friend.getUsername());
                    response.setEmail(friend.getEmail());
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendshipDTO> getPendingRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<Friendship> incomingRequests =
                friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING);

        List<FriendshipDTO> result = new ArrayList<>();

        incomingRequests.forEach(friendship -> {
            FriendshipDTO response = new FriendshipDTO();
            response.setId(friendship.getId());
            response.setSenderId(friendship.getSender().getId());
            response.setSenderUsername(friendship.getSender().getUsername());
            response.setReceiverId(friendship.getReceiver().getId());
            response.setReceiverUsername(friendship.getReceiver().getUsername());
            response.setStatus(friendship.getStatus());
            response.setCreatedAt(friendship.getCreatedAt());
            result.add(response);
        });

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendshipDTO> getOutgoingRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<Friendship> outgoingRequests =
                friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.PENDING);

        List<FriendshipDTO> result = new ArrayList<>();

        outgoingRequests.forEach(friendship -> {
            FriendshipDTO response = new FriendshipDTO();
            response.setId(friendship.getId());
            response.setSenderId(friendship.getSender().getId());
            response.setSenderUsername(friendship.getSender().getUsername());
            response.setReceiverId(friendship.getReceiver().getId());
            response.setReceiverUsername(friendship.getReceiver().getUsername());
            response.setStatus(friendship.getStatus());
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
    @Override
    @Transactional(readOnly = true)
    public List<Long> getFriendIds(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<Friendship> friendships =
                friendshipRepository.findAcceptedFriendships(user);

        return friendships.stream()
                .map(friendship -> {
                    // determine the "other" user
                    if (friendship.getSender().getId().equals(userId)) {
                        return friendship.getReceiver().getId();
                    } else {
                        return friendship.getSender().getId();
                    }
                })
                .toList();
    }
}
