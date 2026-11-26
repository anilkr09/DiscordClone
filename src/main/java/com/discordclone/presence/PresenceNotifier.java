package com.discordclone.presence;

import com.discordclone.cluster.ClusterBus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.repository.UserRepository;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import org.springframework.stereotype.Component;

/** Sends a status to the people allowed to see it: the user's friends, and the user. */
@Component
public class PresenceNotifier {

    private final ClusterBus bus;
    private final FriendDirectory friends;
    private final UserRepository users;
    private final Counter transitions;

    public PresenceNotifier(ClusterBus bus, FriendDirectory friends, UserRepository users, MeterRegistry meters) {
        this.bus = bus;
        this.friends = friends;
        this.users = users;
        this.transitions = meters.counter("presence.transitions");
    }

    public void notifyFriends(Long userId, UserStatus status) {
        transitions.increment();
        WsEvent event = event(userId, status);
        friends.friendNames(userId).forEach(name -> send(name, event));
        users.findById(userId).map(User::getUsername).ifPresent(name -> send(name, event));
    }

    public void notifyUser(String username, Long aboutUserId, UserStatus status) {
        send(username, event(aboutUserId, status));
    }

    private void send(String username, WsEvent event) {
        bus.toUser(username, WsDestinations.PRESENCE, event);
    }

    private static WsEvent event(Long userId, UserStatus status) {
        return new WsEvent(WsEventType.PRESENCE_CHANGED, new PresenceChange(userId, status));
    }
}
