package com.discordclone.integration;

import com.discordclone.dto.MessagePage;
import com.discordclone.dto.MessageResp;
import com.discordclone.model.*;
import com.discordclone.repository.*;
import com.discordclone.service.FriendshipService;
import com.discordclone.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Migrations, constraints, cascades and derived queries against real PostgreSQL. */
class DataModelTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FriendshipRepository friendships;
    @Autowired
    FriendshipService friendshipService;
    @Autowired
    ChannelMemberRepository channelMembers;
    @Autowired
    MessageRepository messages;
    @Autowired
    InviteService inviteService;
    @Autowired
    InviteRepository invites;

    private Friendship friendship(User sender, User receiver) {
        Friendship friendship = new Friendship();
        friendship.setSender(sender);
        friendship.setReceiver(receiver);
        friendship.setStatus(FriendshipStatus.PENDING);
        return friendship;
    }

    private long count(String table, String column, Object value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }

    @Test
    void onePairOfUsersHasOneFriendshipRow() {
        User a = newUser("a");
        User b = newUser("b");
        friendships.saveAndFlush(friendship(a, b));

        assertThrows(DataIntegrityViolationException.class, () -> friendships.saveAndFlush(friendship(b, a)));
    }

    @Test
    void blockingSomeoneWhoSentARequestTurnsTheirRowAround() {
        User a = newUser("a");
        User b = newUser("b");
        friendships.saveAndFlush(friendship(a, b));

        friendshipService.blockUser(b.getId(), a.getId());

        Friendship block = friendships.findBySenderAndReceiver(b, a).orElseThrow();
        assertEquals(FriendshipStatus.BLOCKED, block.getStatus());
        assertTrue(friendships.findBySenderAndReceiver(a, b).isEmpty());
    }

    @Test
    void deletingAServerRemovesItsContentButNoDirectMessages() {
        User owner = newUser("owner");
        User friend = newUser("friend");
        Server server = newServer(owner);
        Long channel = newChannel(server, owner);
        newMessage(channel, owner);
        inviteService.createInvite(server.getId(), 5, Duration.ofDays(1), owner.getId(), true);
        Long dm = channelService.getOrCreateDmChannel(owner.getId(), friend.getId()).getId();
        newMessage(dm, owner);

        serverService.deleteServer(server.getId(), owner.getId());

        assertEquals(0, count("servers", "id", server.getId()));
        assertEquals(0, count("server_members", "server_id", server.getId()));
        assertEquals(0, count("invites", "server_id", server.getId()));
        assertEquals(0, count("channels", "server_id", server.getId()));
        assertEquals(0, count("messages", "channel_id", channel));
        assertEquals(1, count("messages", "channel_id", dm));
    }

    @Test
    void aDirectMessageChannelHasNoServerAndRecordsItsParticipants() throws Exception {
        User a = newUser("a");
        User b = newUser("b");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Long>> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            requests.add(() -> channelService.getOrCreateDmChannel(a.getId(), b.getId()).getId());
        }
        List<Long> ids = new ArrayList<>();
        for (Future<Long> result : pool.invokeAll(requests)) {
            ids.add(result.get());
        }
        pool.shutdown();

        assertEquals(1, ids.stream().distinct().count(), "concurrent requests share one channel");
        assertNull(channelRepository.findById(ids.get(0)).orElseThrow().getServer());
        assertEquals(List.of(a.getId(), b.getId()),
                channelMembers.findUserIds(ids.get(0)).stream().sorted().toList());
    }

    @Test
    void historyPagesByCursorWithoutGapsOrRepeats() {
        User owner = newUser("owner");
        Long channel = newChannel(newServer(owner), owner);
        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            sent.add(newMessage(channel, owner));
        }

        List<String> read = new ArrayList<>();
        String before = null;
        MessagePage page;
        do {
            page = messageService.getChannelMessages(channel, before, 3);
            page.content().forEach(message -> read.add(message.getId()));
            before = read.get(read.size() - 1);
        } while (page.hasMore());

        assertEquals(sent.size(), read.size());
        assertEquals(java.util.Set.copyOf(sent), java.util.Set.copyOf(read));
        MessageResp newest = messageService.getChannelMessages(channel, null, 1).content().get(0);
        assertTrue(Duration.between(newest.getTimestamp(), Instant.now()).abs().toSeconds() < 60);
    }

    @Test
    void anAuthorCanDeleteAMessage() {
        User owner = newUser("owner");
        Long channel = newChannel(newServer(owner), owner);
        String id = newMessage(channel, owner);

        messageService.deleteMessage(id);

        assertFalse(messages.existsById(id));
    }

    @Test
    void aSingleUseInviteAdmitsExactlyOneOfManyConcurrentJoiners() throws Exception {
        User owner = newUser("owner");
        Server server = newServer(owner);
        Invite invite = inviteService.createInvite(server.getId(), 1, Duration.ofDays(1), owner.getId(), true);
        List<Callable<Boolean>> joins = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Long userId = newUser("joiner").getId();
            joins.add(() -> {
                try {
                    inviteService.joinViaInvite(invite.getCode(), userId);
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        int admitted = 0;
        for (Future<Boolean> result : pool.invokeAll(joins)) {
            admitted += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertEquals(1, admitted);
        assertEquals(2, count("server_members", "server_id", server.getId()));
        assertEquals(1, invites.findById(invite.getCode()).orElseThrow().getUses());
    }

    @Test
    void anOpenInviteIsReusedUnlessAFreshOneIsRequested() {
        User owner = newUser("owner");
        Server server = newServer(owner);
        String first = inviteService.createInvite(server.getId(), 5, Duration.ofDays(1), owner.getId(), false).getCode();

        assertEquals(first, inviteService.createInvite(server.getId(), 5, Duration.ofDays(1), owner.getId(), false).getCode());
        assertNotEquals(first, inviteService.createInvite(server.getId(), 5, Duration.ofDays(1), owner.getId(), true).getCode());
    }
}
