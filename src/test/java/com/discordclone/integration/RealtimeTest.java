package com.discordclone.integration;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.service.FriendshipService;
import com.discordclone.service.UserStatusService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Presence and channel events end to end, against real Redis and the default timings. */
// Its own Redis database: other cached test contexts sweep the default one, and with an in-memory
// broker a departure claimed by another instance is announced only to that instance's sockets.
@TestPropertySource(properties = "spring.data.redis.database=3")
class RealtimeTest extends IntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    UserStatusService statuses;
    @Autowired
    FriendshipService friendships;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private final ObjectMapper json = new ObjectMapper();
    private final List<StompSession> sessions = new ArrayList<>();

    private User watcher;
    private User friend;
    private BlockingQueue<JsonNode> presence;

    @BeforeEach
    void befriend() throws Exception {
        watcher = newUser("watcher");
        friend = newUser("friend");
        Long request = friendships.sendFriendRequest(watcher.getId(),
                new FriendRequestPayload(friend.getUsername())).getId();
        friendships.acceptFriendRequest(request, friend.getId());
        presence = subscribe(connect(watcher), "/user/queue/presence");
    }

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    private StompSession connect(User user) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", bearer(user));
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() {
                }).get(15, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    frames.add(json.readTree((byte[]) payload));
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }
        });
        Thread.sleep(300);
        return frames;
    }

    /** The statuses announced about the friend until {@code quiet} passes without another. */
    private List<String> announcedFor(Duration quiet) throws InterruptedException {
        List<String> seen = new ArrayList<>();
        long deadline = System.nanoTime() + quiet.toNanos();
        long left;
        while ((left = deadline - System.nanoTime()) > 0) {
            JsonNode frame = presence.poll(left, TimeUnit.NANOSECONDS);
            if (frame != null && frame.at("/payload/userId").asLong() == friend.getId()) {
                assertEquals("PRESENCE_CHANGED", frame.get("type").asText());
                assertEquals(1, frame.get("version").asInt());
                seen.add(frame.at("/payload/status").asText());
            }
        }
        return seen;
    }

    @Test
    void aCleanCloseShowsOfflineOnceWithinTenSeconds() throws Exception {
        StompSession session = connect(friend);
        assertEquals(List.of("ONLINE"), announcedFor(Duration.ofSeconds(2)));

        long closed = System.nanoTime();
        session.disconnect();
        JsonNode frame = presence.poll(10, TimeUnit.SECONDS);

        assertNotNull(frame, "no OFFLINE within 10 s");
        assertEquals("OFFLINE", frame.at("/payload/status").asText());
        assertTrue(Duration.ofNanos(System.nanoTime() - closed).toSeconds() >= 7, "grace period honoured");
        assertEquals(List.of(), announcedFor(Duration.ofSeconds(3)));
    }

    @Test
    void reloadingAndClosingOneOfTwoTabsShowNothing() throws Exception {
        StompSession first = connect(friend);
        StompSession second = connect(friend);
        assertEquals(List.of("ONLINE"), announcedFor(Duration.ofSeconds(2)));

        second.disconnect();          // one of two tabs
        first.disconnect();           // then a reload of the other
        Thread.sleep(2000);
        connect(friend);

        assertEquals(List.of(), announcedFor(Duration.ofSeconds(10)));
    }

    @Test
    void anUncleanDropShowsOfflineExactlyOnceWithinThirtyFiveSeconds() throws Exception {
        // A client that heartbeats once and then vanishes without closing anything.
        statuses.handleHeartbeat(friend.getId(), "vanished");

        List<String> seen = announcedFor(Duration.ofSeconds(35));

        assertEquals("OFFLINE", seen.get(seen.size() - 1), seen.toString());
        assertEquals(1, seen.stream().filter("OFFLINE"::equals).count(), seen.toString());
        assertEquals(com.discordclone.model.UserStatus.OFFLINE, statuses.getUserStatus(friend.getId()));
    }

    @Test
    void strangersHearNothing() throws Exception {
        User stranger = newUser("stranger");
        BlockingQueue<JsonNode> overheard = subscribe(connect(stranger), "/user/queue/presence");

        connect(friend);

        assertEquals(List.of("ONLINE"), announcedFor(Duration.ofSeconds(2)));
        assertTrue(overheard.stream().noneMatch(f -> f.at("/payload/userId").asLong() == friend.getId()));
        assertFalse(friendships.areFriends(stranger.getId(), friend.getId()));
    }

    @Test
    void channelEventsReachOnlyMembersOfThatServer() throws Exception {
        Server server = newServer(watcher);
        BlockingQueue<JsonNode> events =
                subscribe(connect(watcher), "/topic/servers/" + server.getId() + "/channels");
        StompSession outsider = connect(newUser("outsider"));
        BlockingQueue<JsonNode> overheard =
                subscribe(outsider, "/topic/servers/" + server.getId() + "/channels");

        Long channel = newChannel(server, watcher);
        channelService.deleteChannel(server.getId(), channel);

        assertEquals("CHANNEL_CREATED", events.poll(5, TimeUnit.SECONDS).get("type").asText());
        assertEquals("CHANNEL_DELETED", events.poll(5, TimeUnit.SECONDS).get("type").asText());
        assertTrue(overheard.isEmpty());
        assertFalse(outsider.isConnected());
    }

    @Test
    void theBrokerNegotiatesHeartbeats() throws Exception {
        client.setTaskScheduler(new org.springframework.scheduling.concurrent.ConcurrentTaskScheduler());
        CompletableFuture<long[]> negotiated = new CompletableFuture<>();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", bearer(watcher));
        sessions.add(client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connected) {
                        negotiated.complete(connected.getHeartbeat());
                    }
                }).get(15, TimeUnit.SECONDS));

        assertArrayEquals(new long[]{10_000, 10_000}, negotiated.get(5, TimeUnit.SECONDS));
    }
}
