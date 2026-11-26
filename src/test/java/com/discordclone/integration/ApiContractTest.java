package com.discordclone.integration;

import com.discordclone.cluster.ClusterBus;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.service.ChatBroadcast;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Status codes and error bodies, and broadcasts that wait for the commit. */
@AutoConfigureMockMvc
class ApiContractTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    ApplicationEventPublisher events;
    @MockitoSpyBean
    ClusterBus bus;

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            GET    | /api/no-such-route                      |                         | 404
            DELETE | /api/users                              |                         | 405
            GET    | /api/users/999999999                    |                         | 404
            GET    | /api/users/not-a-number                 |                         | 400
            GET    | /api/users/username/nobody-by-this-name |                         | 404
            GET    | /api/messages/channels/999999999        |                         | 404
            DELETE | /api/messages/no-such-message           |                         | 404
            POST   | /api/invites/join/nosuch                |                         | 404
            POST   | /api/servers                            | {}                      | 400
            POST   | /api/servers                            | {"name":"   "}          | 400
            POST   | /api/servers                            | not json                | 400
            POST   | /api/friends/request                    | {"username":""}         | 400
            POST   | /api/friends/request                    | {"username":"nobody-x"} | 404
            POST   | /api/users/status/custom?status=nonsense|                         | 400
            POST   | /api/auth/login                         | {}                      | 400
            POST   | /api/auth/login                         | {"username":"x","password":"y"} | 401
            POST   | /api/auth/register                      | {"username":"a b","email":"bad","password":"1"} | 400
            """)
    void errorsCarryTheRightStatus(String method, String path, String body, int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path).header("Authorization", bearer(newUser("caller")));
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mvc.perform(builder)
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void usernamesAreUniqueAndMatchedRegardlessOfCase() throws Exception {
        User user = newUser("CaseUser");
        String body = """
                {"username":"  %s ","email":"%s@else.example.com","password":"long-enough"}"""
                .formatted(user.getUsername().toLowerCase(), unique());

        mvc.perform(request(HttpMethod.POST, "/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(request(HttpMethod.POST, "/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"test-password\"}"
                                .formatted(user.getUsername().toUpperCase())))
                .andExpect(status().isOk());
    }

    @Test
    void theOwnerCanChangeARoleButNotOwnership() throws Exception {
        User owner = newUser("owner");
        User member = newUser("member");
        Server server = newServer(owner);
        serverService.addMember(server.getId(), member.getId());
        String path = "/api/servers/" + server.getId() + "/members/" + member.getId() + "/role";

        for (String[] attempt : new String[][]{{"ADMIN", "200"}, {"OWNER", "400"}}) {
            mvc.perform(request(HttpMethod.PUT, path).header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + attempt[0] + "\"}"))
                    .andExpect(status().is(Integer.parseInt(attempt[1])));
        }
        assertTrue(serverService.isUserAdmin(server.getId(), member.getId()));
    }

    @Test
    void anUnexpectedFailureDoesNotDescribeItself() throws Exception {
        // A null byte is rejected by PostgreSQL, which surfaces as an unexpected data access error.
        var response = mvc.perform(request(HttpMethod.GET, "/api/users/search?prefix=%00")
                .header("Authorization", bearer(newUser("caller")))).andReturn().getResponse();

        assertFalse(response.getContentAsString().toLowerCase().contains("sql"));
        assertFalse(response.getContentAsString().toLowerCase().contains("jdbc"));
    }

    @Test
    void nothingIsBroadcastWhenTheTransactionRollsBack() {
        transactions.executeWithoutResult(tx -> {
            events.publishEvent(ChatBroadcast.toTopic("/topic/rolled-back", "x"));
            tx.setRollbackOnly();
        });
        transactions.executeWithoutResult(tx -> {
            events.publishEvent(ChatBroadcast.toTopic("/topic/committed", "y"));
            verify(bus, never()).toTopic(eq("/topic/committed"), any());
        });

        verify(bus, never()).toTopic(eq("/topic/rolled-back"), any());
        verify(bus).toTopic("/topic/committed", "y");
    }
}
