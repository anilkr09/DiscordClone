package com.discordclone.presence;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.repository.UserRepository;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Sends a status to the people allowed to see it: the user's friends, and the user. */
@Component
@RequiredArgsConstructor
public class PresenceNotifier {

    private final SimpMessagingTemplate messaging;
    private final FriendDirectory friends;
    private final UserRepository users;

    public void notifyFriends(Long userId, UserStatus status) {
        WsEvent event = event(userId, status);
        friends.friendNames(userId).forEach(name -> send(name, event));
        users.findById(userId).map(User::getUsername).ifPresent(name -> send(name, event));
    }

    public void notifyUser(String username, Long aboutUserId, UserStatus status) {
        send(username, event(aboutUserId, status));
    }

    private void send(String username, WsEvent event) {
        messaging.convertAndSendToUser(username, WsDestinations.PRESENCE, event);
    }

    private static WsEvent event(Long userId, UserStatus status) {
        return new WsEvent(WsEventType.PRESENCE_CHANGED, new PresenceChange(userId, status));
    }
}
