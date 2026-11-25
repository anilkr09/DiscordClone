package com.discordclone.integration;

import com.discordclone.model.Channel;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.payload.ChannelPayload;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.ServerPayload;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.security.JwtService;
import com.discordclone.service.ChannelService;
import com.discordclone.service.MessageService;
import com.discordclone.service.ServerService;
import com.discordclone.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/** Runs the whole application against real PostgreSQL and Redis containers. Needs Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

    // Started once and shared by every integration test class; Testcontainers removes them afterwards.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("app.jwt.secret", () -> "integration-test-only-signing-key-".repeat(3));
    }

    @Autowired
    protected UserService userService;
    @Autowired
    protected ServerService serverService;
    @Autowired
    protected ChannelService channelService;
    @Autowired
    protected ChannelRepository channelRepository;
    @Autowired
    protected MessageService messageService;
    @Autowired
    protected JwtService jwtService;

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected User newUser(String prefix) {
        String name = prefix + unique();
        return userService.createUser(User.builder()
                .username(name).email(name + "@example.com").password("test-password").build());
    }

    protected Server newServer(User owner) {
        ServerPayload payload = new ServerPayload();
        payload.setName("server-" + unique());
        return serverService.createServer(payload, owner.getId());
    }

    protected Long newChannel(Server server, User admin) {
        ChannelPayload payload = new ChannelPayload();
        payload.setName("channel-" + unique());
        return channelService.createChannel(payload, server.getId(), admin.getId()).getId();
    }

    protected String newMessage(Long channelId, User sender) {
        return messageService.sendMessage(new MessageRequest(false, "hello", channelId, null), sender.getId())
                .getId();
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getId());
    }
}
