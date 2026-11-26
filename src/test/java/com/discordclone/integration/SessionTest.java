package com.discordclone.integration;

import com.discordclone.model.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Sign-in, refresh rotation, reuse detection and sign-out over HTTP. */
@AutoConfigureMockMvc
class SessionTest extends IntegrationTest {

    private static final String CSRF = "X-CSRF-Protection";

    @Autowired
    MockMvc mvc;
    @Autowired
    TestRestTemplate rest;

    private MockHttpServletResponse login(User user) throws Exception {
        return mvc.perform(request(HttpMethod.POST, "/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"test-password\"}"))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(HttpMethod method, Cookie cookie) throws Exception {
        var builder = request(method, "/api/auth/refresh").header(CSRF, "1");
        if (cookie != null) {
            builder.cookie(cookie);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private static String accessToken(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        return body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1").strip();
    }

    private int callApi(String token) throws Exception {
        return mvc.perform(request(HttpMethod.GET, "/api/friends").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void theRefreshTokenIsOnlyEverInAProtectedCookie() throws Exception {
        MockHttpServletResponse response = login(newUser("cookie"));

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        for (String attribute : new String[]{"HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth/refresh"}) {
            assertTrue(header.contains(attribute), header);
        }
        assertFalse(response.getContentAsString().contains(response.getCookie("refresh_token").getValue()));
    }

    @Test
    void refreshingRotatesTheTokenAndReuseRevokesTheLogin() throws Exception {
        Cookie first = login(newUser("rotate")).getCookie("refresh_token");

        MockHttpServletResponse renewed = refresh(HttpMethod.POST, first);
        assertEquals(200, renewed.getStatus());
        Cookie second = renewed.getCookie("refresh_token");
        assertNotEquals(first.getValue(), second.getValue());
        assertEquals(200, callApi(accessToken(renewed)));

        assertEquals(401, refresh(HttpMethod.POST, first).getStatus(), "a used token");
        assertEquals(401, refresh(HttpMethod.POST, second).getStatus(), "its successor, after the reuse");
    }

    @Test
    void neitherKindOfTokenPassesForTheOther() throws Exception {
        MockHttpServletResponse signedIn = login(newUser("kinds"));
        String access = accessToken(signedIn);
        String refreshToken = signedIn.getCookie("refresh_token").getValue();

        assertEquals(401, refresh(HttpMethod.POST, new Cookie("refresh_token", access)).getStatus());
        assertEquals(401, callApi(refreshToken));
    }

    @Test
    void refreshNeedsTheCsrfHeaderAndACookie() throws Exception {
        Cookie cookie = login(newUser("csrf")).getCookie("refresh_token");

        assertEquals(403, mvc.perform(request(HttpMethod.POST, "/api/auth/refresh").cookie(cookie))
                .andReturn().getResponse().getStatus());
        assertEquals(401, refresh(HttpMethod.POST, null).getStatus());
    }

    @Test
    void signingOutEndsTheLogin() throws Exception {
        Cookie cookie = login(newUser("logout")).getCookie("refresh_token");

        MockHttpServletResponse out = refresh(HttpMethod.DELETE, cookie);

        assertEquals(204, out.getStatus());
        assertEquals(0, out.getCookie("refresh_token").getMaxAge());
        assertEquals(401, refresh(HttpMethod.POST, cookie).getStatus());
    }

    @Test
    void strictTransportSecurityIsSentBehindATlsProxy() {
        HttpHeaders forwarded = new HttpHeaders();
        forwarded.add("X-Forwarded-Proto", "https");

        var response = rest.exchange("/livez", HttpMethod.GET, new HttpEntity<>(forwarded), String.class);

        assertNotNull(response.getHeaders().getFirst("Strict-Transport-Security"));
    }
}
