package com.discordclone.integration;

import com.discordclone.security.LoginSessions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** An open socket never outlives its token or its login. */
@TestPropertySource(properties = "app.jwt.expiration=6s")
class SocketLifetimeTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(15, TimeUnit.SECONDS);
    }

    @Test
    void theSocketIsClosedWhenItsTokenExpires() throws Exception {
        StompSession session = connect(loginSessions.open(newUser("expiry").getId()).accessToken());
        assertTrue(session.isConnected());

        await().atMost(Duration.ofSeconds(15)).until(() -> !session.isConnected());
    }

    @Test
    void signingOutClosesThatLoginsSocketsOnly() throws Exception {
        Long userId = newUser("two-logins").getId();
        LoginSessions.Tokens laptop = loginSessions.open(userId);
        LoginSessions.Tokens phone = loginSessions.open(userId);
        StompSession onLaptop = connect(laptop.accessToken());
        StompSession onPhone = connect(phone.accessToken());

        loginSessions.close(laptop.refreshToken());

        await().atMost(Duration.ofSeconds(3)).until(() -> !onLaptop.isConnected());
        assertTrue(onPhone.isConnected());
        // The still-unexpired access token of a closed login cannot open another socket.
        assertThrows(ExecutionException.class, () -> connect(laptop.accessToken()));
    }
}
