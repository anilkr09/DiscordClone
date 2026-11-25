package com.discordclone.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.limits.login.capacity=3",
        "app.rate-limit.limits.login.period=1h"
})
class RateLimitTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private int attempt(String username) throws Exception {
        return mvc.perform(request(HttpMethod.POST, "/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void repeatedPasswordGuessesForOneNameAreRefused() throws Exception {
        String name = "victim" + unique();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // Case and spacing do not earn a fresh allowance.
            statuses.add(attempt(i % 2 == 0 ? name : " " + name.toUpperCase()));
        }

        assertEquals(List.of(401, 401, 401, 429, 429), statuses);
        assertEquals(401, attempt("someone-else" + unique()));
    }
}
