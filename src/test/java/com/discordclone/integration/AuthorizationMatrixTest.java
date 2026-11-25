package com.discordclone.integration;

import com.discordclone.model.Channel;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every protected endpoint against every kind of caller. "member" is an ordinary member of the
 * server and the author of the test message; "owner" owns the server; "stranger" is registered
 * but belongs to nothing.
 */
@AutoConfigureMockMvc
class AuthorizationMatrixTest extends IntegrationTest {

    enum Caller { OWNER, MEMBER, STRANGER }

    private static final Set<Caller> EVERYONE = Set.of(Caller.values());
    private static final Set<Caller> MEMBERS = Set.of(Caller.OWNER, Caller.MEMBER);
    private static final Set<Caller> OWNER = Set.of(Caller.OWNER);
    private static final Set<Caller> AUTHOR = Set.of(Caller.MEMBER);

    record Endpoint(HttpMethod method, String path, String body, Set<Caller> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static Stream<Endpoint> endpoints() {
        String named = "{\"name\":\"renamed-{random}\",\"description\":\"d\"}";
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/servers/{server}", null, MEMBERS),
                new Endpoint(HttpMethod.PUT, "/api/servers/{server}", named, OWNER),
                new Endpoint(HttpMethod.DELETE, "/api/servers/{server}", null, OWNER),
                new Endpoint(HttpMethod.POST, "/api/servers/{server}/members/{outsider}", null, OWNER),
                new Endpoint(HttpMethod.DELETE, "/api/servers/{server}/members/{bystander}", null, OWNER),
                new Endpoint(HttpMethod.DELETE, "/api/servers/{server}/members/{member}", null, MEMBERS),
                new Endpoint(HttpMethod.POST, "/api/channels/{server}", named, OWNER),
                new Endpoint(HttpMethod.GET, "/api/channels/{server}", null, MEMBERS),
                new Endpoint(HttpMethod.GET, "/api/channels/{server}/{channel}", null, MEMBERS),
                new Endpoint(HttpMethod.PUT, "/api/channels/{server}/{channel}", named, OWNER),
                new Endpoint(HttpMethod.DELETE, "/api/channels/{server}/{channel}", null, OWNER),
                new Endpoint(HttpMethod.POST, "/api/dms/{outsider}", null, EVERYONE),
                new Endpoint(HttpMethod.POST, "/api/invites/create?serverId={server}", null, MEMBERS),
                new Endpoint(HttpMethod.GET, "/api/messages/channels/{channel}", null, MEMBERS),
                new Endpoint(HttpMethod.PUT, "/api/messages/{message}", "{\"content\":\"edited\"}", AUTHOR),
                new Endpoint(HttpMethod.DELETE, "/api/messages/{message}", null, AUTHOR),
                new Endpoint(HttpMethod.GET, "/api/users", null, EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/users/{member}", null, EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/users/search?prefix=member", null, EVERYONE),
                new Endpoint(HttpMethod.GET, "/api/friends", null, EVERYONE)
        );
    }

    @Autowired
    private MockMvc mvc;

    private User owner;
    private User member;
    private User stranger;
    private User bystander;
    private User outsider;
    private Server server;
    private Long channelId;
    private String messageId;

    @BeforeEach
    void setUp() {
        owner = newUser("owner");
        member = newUser("member");
        stranger = newUser("stranger");
        bystander = newUser("bystander");
        outsider = newUser("outsider");
        server = newServer(owner);
        serverService.addMember(server.getId(), member.getId());
        serverService.addMember(server.getId(), bystander.getId());
        channelId = newChannel(server, owner);
        messageId = newMessage(channelId, member);
    }

    private MockHttpServletRequestBuilder build(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), fill(endpoint.path()));
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(fill(endpoint.body()));
        }
        return builder;
    }

    private String fill(String template) {
        return template.replace("{server}", server.getId().toString())
                .replace("{channel}", channelId.toString())
                .replace("{message}", messageId)
                .replace("{member}", member.getId().toString())
                .replace("{bystander}", bystander.getId().toString())
                .replace("{outsider}", outsider.getId().toString())
                .replace("{random}", unique());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anonymousCallersAreUnauthorized(Endpoint endpoint) throws Exception {
        assertEquals(401, mvc.perform(build(endpoint)).andReturn().getResponse().getStatus());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void strangersAndMembersAreHeldToTheirRole(Endpoint endpoint) throws Exception {
        // Denied callers first, so an allowed delete cannot remove what a later check needs.
        for (Caller caller : new Caller[]{Caller.STRANGER, Caller.MEMBER, Caller.OWNER}) {
            if (!endpoint.allowed().contains(caller)) {
                assertEquals(403, perform(endpoint, caller), caller + " must be refused");
            }
        }
        // Then one allowed caller: it must get past authorization.
        for (Caller caller : new Caller[]{Caller.OWNER, Caller.MEMBER, Caller.STRANGER}) {
            if (endpoint.allowed().contains(caller)) {
                int status = perform(endpoint, caller);
                assertTrue(status != 401 && status != 403, caller + " was refused with " + status);
                return;
            }
        }
    }

    private int perform(Endpoint endpoint, Caller caller) throws Exception {
        User user = switch (caller) {
            case OWNER -> owner;
            case MEMBER -> member;
            case STRANGER -> stranger;
        };
        return mvc.perform(build(endpoint).header("Authorization", bearer(user)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void onlyParticipantsCanReadADirectMessageChannel() throws Exception {
        Channel dm = channelService.getOrCreateDmChannel(member.getId(), outsider.getId());
        String path = "/api/messages/channels/" + dm.getId();

        for (User participant : new User[]{member, outsider}) {
            assertEquals(200, mvc.perform(request(HttpMethod.GET, path)
                    .header("Authorization", bearer(participant))).andReturn().getResponse().getStatus());
        }
        for (User other : new User[]{owner, stranger}) {
            assertEquals(403, mvc.perform(request(HttpMethod.GET, path)
                    .header("Authorization", bearer(other))).andReturn().getResponse().getStatus());
        }
    }

    @Test
    void channelCannotBeAddressedThroughAnotherServer() throws Exception {
        Server other = newServer(stranger);
        int status = mvc.perform(request(HttpMethod.DELETE, "/api/channels/" + other.getId() + "/" + channelId)
                .header("Authorization", bearer(stranger))).andReturn().getResponse().getStatus();

        assertEquals(404, status);
        assertTrue(channelRepository.existsById(channelId));
    }

    @Test
    void userCreationAndOverwriteEndpointIsGone() throws Exception {
        int status = mvc.perform(request(HttpMethod.POST, "/api/users")
                        .header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + owner.getId() + ",\"username\":\"taken-over\"}"))
                .andReturn().getResponse().getStatus();

        assertNotEquals(200, status);
        assertEquals(owner.getUsername(), userService.getUserById(owner.getId()).getUsername());
    }

    @Test
    void noResponseBodyCarriesAPasswordOrHash() throws Exception {
        channelService.getOrCreateDmChannel(owner.getId(), member.getId());
        for (String path : new String[]{
                "/api/users", "/api/users/" + member.getId(), "/api/users/username/" + member.getUsername(),
                "/api/users/search?prefix=member", "/api/servers", "/api/servers/" + server.getId(),
                "/api/channels/" + server.getId(), "/api/channels/" + server.getId() + "/" + channelId,
                "/api/messages/channels/" + channelId}) {
            var response = mvc.perform(request(HttpMethod.GET, path).header("Authorization", bearer(owner)))
                    .andReturn().getResponse();
            assertEquals(200, response.getStatus(), path);
            String body = response.getContentAsString();
            assertFalse(body.contains("password") || body.contains("$2a$"), path + " -> " + body);
        }
    }
}
