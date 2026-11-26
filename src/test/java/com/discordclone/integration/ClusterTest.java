package com.discordclone.integration;

import com.discordclone.DiscordCloneApplication;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.payload.FriendRequestPayload;
import com.discordclone.security.LoginSessions;
import com.discordclone.service.FriendshipService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
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
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Two instances sharing PostgreSQL and Redis: users on different ones reach each other. */
class ClusterTest extends IntegrationTest {

    private static ConfigurableApplicationContext second;

    @LocalServerPort
    private int firstPort;
    @Autowired
    Environment environment;
    @Autowired
    FriendshipService friendships;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private final ObjectMapper json = new ObjectMapper();

    @AfterAll
    static void stopSecondInstance() {
        if (second != null) {
            second.close();
            second = null;
        }
    }

    private int secondPort() {
        if (second == null) {
            String[] properties = {"spring.datasource.url", "spring.datasource.username",
                    "spring.datasource.password", "spring.data.redis.host", "spring.data.redis.port",
                    "spring.data.redis.password", "app.jwt.secret"};
            var args = new java.util.ArrayList<String>();
            args.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
            args.add("--spring.datasource.username=" + POSTGRES.getUsername());
            args.add("--spring.datasource.password=" + POSTGRES.getPassword());
            for (int i = 3; i < properties.length; i++) {
                args.add("--" + properties[i] + "=" + environment.getProperty(properties[i]));
            }
            args.add("--server.port=0");
            args.add("--management.server.port=0");
            second = new SpringApplicationBuilder(DiscordCloneApplication.class)
                    .run(args.toArray(String[]::new));
        }
        return Integer.parseInt(second.getEnvironment().getProperty("local.server.port"));
    }

    private StompSession connect(int port, String accessToken) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(15, TimeUnit.SECONDS);
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

    private void say(StompSession session, Long channelId, String content) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/chat.send");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, ("{\"channelId\":" + channelId + ",\"content\":\"" + content + "\"}")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void channelMessagesDirectMessagesPresenceAndSignOutCrossInstances() throws Exception {
        User alice = newUser("alice");
        User bob = newUser("bob");
        Long request = friendships.sendFriendRequest(alice.getId(),
                new FriendRequestPayload(bob.getUsername())).getId();
        friendships.acceptFriendRequest(request, bob.getId());
        Server server = newServer(alice);
        serverService.addMember(server.getId(), bob.getId());
        Long channel = newChannel(server, alice);
        Long dm = channelService.getOrCreateDmChannel(alice.getId(), bob.getId()).getId();

        StompSession onFirst = connect(firstPort, loginSessions.open(alice.getId()).accessToken());
        BlockingQueue<JsonNode> presence = subscribe(onFirst, "/user/queue/presence");
        BlockingQueue<JsonNode> direct = subscribe(onFirst, "/user/queue/messages");
        LoginSessions.Tokens bobs = loginSessions.open(bob.getId());
        StompSession onSecond = connect(secondPort(), bobs.accessToken());
        BlockingQueue<JsonNode> inChannel = subscribe(onSecond, "/topic/channels/" + channel + "/messages");

        JsonNode online = presence.poll(5, TimeUnit.SECONDS);
        assertNotNull(online, "presence from the other instance");
        assertEquals(bob.getId(), online.at("/payload/userId").asLong());

        say(onFirst, channel, "to the channel");
        assertEquals("to the channel", inChannel.poll(5, TimeUnit.SECONDS).at("/payload/content").asText());

        say(onSecond, dm, "to alice");
        assertEquals("to alice", direct.poll(5, TimeUnit.SECONDS).at("/payload/content").asText());
        assertNull(inChannel.poll(500, TimeUnit.MILLISECONDS), "each message arrives once");

        // Signing out on the first instance closes the socket held by the second.
        loginSessions.close(bobs.refreshToken());
        await().atMost(Duration.ofSeconds(5)).until(() -> !onSecond.isConnected());
        onFirst.disconnect();
    }
}
