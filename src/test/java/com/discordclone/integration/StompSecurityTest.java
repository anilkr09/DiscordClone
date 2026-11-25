package com.discordclone.integration;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StompSecurityTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private final List<StompSession> sessions = new ArrayList<>();

    private User owner;
    private User stranger;
    private Long channelId;

    @BeforeEach
    void setUp() {
        owner = newUser("owner");
        stranger = newUser("stranger");
        Server server = newServer(owner);
        channelId = newChannel(server, owner);
    }

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    private StompSession connect(String authorization) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (authorization != null) {
            headers.add("Authorization", authorization);
        }
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<String> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        // The simple broker sends no receipts; give it a moment to register the subscription.
        Thread.sleep(300);
        return frames;
    }

    private void send(StompSession session, String destination, String json) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, json.getBytes(StandardCharsets.UTF_8));
    }

    private String chat(Long channel, String content) {
        return "{\"channelId\":" + channel + ",\"content\":\"" + content + "\"}";
    }

    @Test
    void connectWithoutAValidTokenIsRefused() {
        assertThrows(ExecutionException.class, () -> connect(null));
        assertThrows(ExecutionException.class, () -> connect("Bearer not-a-token"));
    }

    @Test
    void memberReceivesChannelMessages() throws Exception {
        StompSession session = connect(bearer(owner));
        BlockingQueue<String> frames = subscribe(session, "/topic/channels/" + channelId + "/messages");

        send(session, "/app/chat.send", chat(channelId, "hi"));

        String frame = frames.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame);
        assertTrue(frame.contains("\"hi\""));
    }

    @Test
    void strangerCannotSubscribeToAChannel() throws Exception {
        for (String destination : new String[]{"/topic/channels/" + channelId + "/messages",
                "/topic/channels/*/messages", "/topic/**", "/queue/**", "/user/queue/**"}) {
            StompSession intruder = connect(bearer(stranger));
            BlockingQueue<String> overheard = subscribe(intruder, destination);
            StompSession sender = connect(bearer(owner));

            send(sender, "/app/chat.send", chat(channelId, "secret"));

            assertNull(overheard.poll(1, TimeUnit.SECONDS), destination);
            assertFalse(intruder.isConnected(), destination + " should end the session");
        }
    }

    @Test
    void strangerCannotSendIntoAChannel() throws Exception {
        StompSession member = connect(bearer(owner));
        BlockingQueue<String> frames = subscribe(member, "/topic/channels/" + channelId + "/messages");

        send(connect(bearer(stranger)), "/app/chat.send", chat(channelId, "intrusion"));

        assertNull(frames.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void clientsCannotPublishToBrokerDestinations() throws Exception {
        StompSession member = connect(bearer(owner));
        BlockingQueue<String> channel = subscribe(member, "/topic/channels/" + channelId + "/messages");
        BlockingQueue<String> direct = subscribe(member, "/user/queue/messages");

        for (String destination : new String[]{"/topic/channels/" + channelId + "/messages",
                "/user/" + owner.getUsername() + "/queue/messages"}) {
            send(connect(bearer(stranger)), destination, "{\"content\":\"forged\"}");
        }

        assertNull(channel.poll(1, TimeUnit.SECONDS));
        assertNull(direct.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void directMessageReachesAMixedCaseUsernameWhateverTheClientClaims() throws Exception {
        User friend = newUser("MixedCase");
        Long dm = channelService.getOrCreateDmChannel(owner.getId(), friend.getId()).getId();
        BlockingQueue<String> friendQueue = subscribe(connect(bearer(friend)), "/user/queue/messages");
        BlockingQueue<String> strangerQueue = subscribe(connect(bearer(stranger)), "/user/queue/messages");

        send(connect(bearer(owner)), "/app/chat.send",
                "{\"channelId\":" + dm + ",\"content\":\"private\",\"dm\":true,\"receiver\":\""
                        + stranger.getUsername() + "\"}");

        assertNotNull(friendQueue.poll(5, TimeUnit.SECONDS));
        assertNull(strangerQueue.poll(1, TimeUnit.SECONDS));
    }
}
