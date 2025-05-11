package com.discordclone.service;

import com.discordclone.model.*;
import com.discordclone.repository.InviteRepository;
import com.discordclone.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private ServerService serverService;

    @Mock
    private UserService userService;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private InviteService inviteService;

    private Server testServer;
    private User testUser;
    private Invite testInvite;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");

        testServer = new Server();
        testServer.setId(1L);
        testServer.setName("Test Server");
        testServer.setOwner(testUser);

        testInvite = Invite.builder()
                .code("testcode")
                .server(testServer)
                .maxUses(10)
                .uses(0)
                .expiry(LocalDateTime.now().plusDays(1))
                .build();

        testMember = Member.builder()
                .userId(testUser.getId())
                .serverId(testServer.getId())
                .user(testUser)
                .server(testServer)
                .nickname(testUser.getUsername())
                .role(Role.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createInvite_Success() {
        when(serverService.isUserMember(testServer.getId(), testUser.getId())).thenReturn(true);
        when(serverService.getServerById(testServer.getId())).thenReturn(testServer);
        when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);

        Invite result = inviteService.createInvite(testServer.getId(), 10, Duration.ofDays(1), testUser.getId());

        assertNotNull(result);
        assertEquals(testServer, result.getServer());
        assertEquals(10, result.getMaxUses());
        assertEquals(0, result.getUses());
        verify(inviteRepository).save(any(Invite.class));
    }

    @Test
    void createInvite_NotMember_ThrowsException() {
        when(serverService.isUserMember(testServer.getId(), testUser.getId())).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> inviteService.createInvite(testServer.getId(), 10, Duration.ofDays(1), testUser.getId()));
        verify(inviteRepository, never()).save(any(Invite.class));
    }

    @Test
    void joinViaInvite_Success() {
        User joiningUser = new User();
        joiningUser.setId(2L);
        joiningUser.setUsername("joiningUser");

        when(inviteRepository.findById(testInvite.getCode())).thenReturn(Optional.of(testInvite));
        when(userService.getUserById(joiningUser.getId())).thenReturn(joiningUser);
        when(memberRepository.existsByUserIdAndServerId(joiningUser.getId(), testServer.getId())).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);
        when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);

        Server result = inviteService.joinViaInvite(testInvite.getCode(), joiningUser.getId());

        assertNotNull(result);
        assertEquals(testServer.getId(), result.getId());
        verify(memberRepository).save(any(Member.class));
        verify(inviteRepository).save(any(Invite.class));
    }

    @Test
    void joinViaInvite_InvalidCode_ThrowsException() {
        when(inviteRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> inviteService.joinViaInvite("invalidcode", testUser.getId()));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void joinViaInvite_ExpiredInvite_ThrowsException() {
        testInvite.setExpiry(LocalDateTime.now().minusDays(1));
        when(inviteRepository.findById(testInvite.getCode())).thenReturn(Optional.of(testInvite));

        assertThrows(RuntimeException.class,
                () -> inviteService.joinViaInvite(testInvite.getCode(), testUser.getId()));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void joinViaInvite_MaxUsesReached_ThrowsException() {
        testInvite.setUses(testInvite.getMaxUses());
        when(inviteRepository.findById(testInvite.getCode())).thenReturn(Optional.of(testInvite));

        assertThrows(RuntimeException.class,
                () -> inviteService.joinViaInvite(testInvite.getCode(), testUser.getId()));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void joinViaInvite_AlreadyMember_ThrowsException() {
        when(inviteRepository.findById(testInvite.getCode())).thenReturn(Optional.of(testInvite));
        when(userService.getUserById(testUser.getId())).thenReturn(testUser);
        when(memberRepository.existsByUserIdAndServerId(testUser.getId(), testServer.getId())).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> inviteService.joinViaInvite(testInvite.getCode(), testUser.getId()));
        verify(memberRepository, never()).save(any(Member.class));
    }
}