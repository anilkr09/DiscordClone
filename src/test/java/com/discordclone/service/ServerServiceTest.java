package com.discordclone.service;

import com.discordclone.model.*;
import com.discordclone.repository.MemberRepository;
import com.discordclone.repository.ServerRepository;
import com.discordclone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ServerService serverService;

    private User testUser;
    private Server testServer;
    private Member testMember;
    private MemberId testMemberId;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");

        testServer = new Server();
        testServer.setId(1L);
        testServer.setName("Test Server");
        testServer.setDescription("Test Description");
        testServer.setOwner(testUser);

        testMemberId = new MemberId(testUser.getId(), testServer.getId());
        
        testMember = Member.builder()
                .userId(testUser.getId())
                .serverId(testServer.getId())
                .user(testUser)
                .server(testServer)
                .nickname(testUser.getUsername())
                .role(Role.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createServer_Success() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(serverRepository.save(any(Server.class))).thenReturn(testServer);
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);

        Server result = serverService.createServer(testServer, testUser.getId());

        assertNotNull(result);
        assertEquals(testServer.getName(), result.getName());
        assertEquals(testUser, result.getOwner());
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void createServer_UserNotFound_ThrowsException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> serverService.createServer(testServer, 999L));
        verify(serverRepository, never()).save(any(Server.class));
    }

    @Test
    void getServerById_Success() {
        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));

        Server result = serverService.getServerById(testServer.getId());

        assertNotNull(result);
        assertEquals(testServer.getId(), result.getId());
        assertEquals(testServer.getName(), result.getName());
    }

    @Test
    void getUserServers_Success() {
        List<Server> servers = Arrays.asList(testServer);
        when(memberRepository.findServersByUserId(testUser.getId())).thenReturn(servers);

        List<Server> result = serverService.getUserServers(testUser.getId());

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testServer.getName(), result.get(0).getName());
    }

    @Test
    void addMember_Success() {
        User newMember = new User();
        newMember.setId(2L);
        newMember.setUsername("newMember");

        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(newMember.getId())).thenReturn(newMember);
        when(memberRepository.existsById(any(MemberId.class))).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);

        Server result = serverService.addMember(testServer.getId(), newMember.getId());

        assertNotNull(result);
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void addMember_AlreadyMember_ThrowsException() {
        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(testUser.getId())).thenReturn(testUser);
        when(memberRepository.existsById(any(MemberId.class))).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> serverService.addMember(testServer.getId(), testUser.getId()));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void removeMember_Success() {
        User member = new User();
        member.setId(2L);
        
        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(member.getId())).thenReturn(member);
        when(memberRepository.findById(any(MemberId.class))).thenReturn(Optional.of(testMember));
        doNothing().when(memberRepository).delete(any(Member.class));

        Server result = serverService.removeMember(testServer.getId(), member.getId());

        assertNotNull(result);
        verify(memberRepository).delete(any(Member.class));
    }

    @Test
    void removeMember_OwnerAttempt_ThrowsException() {
        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(testUser.getId())).thenReturn(testUser);

        assertThrows(RuntimeException.class,
                () -> serverService.removeMember(testServer.getId(), testUser.getId()));
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    void updateServer_Success() {
        Server updatedServer = new Server();
        updatedServer.setName("Updated Server");
        updatedServer.setDescription("Updated Description");

        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(testUser.getId())).thenReturn(testUser);
        when(serverRepository.save(any(Server.class))).thenReturn(updatedServer);

        Server result = serverService.updateServer(testServer.getId(), updatedServer, testUser.getId());

        assertNotNull(result);
        assertEquals(updatedServer.getName(), result.getName());
        assertEquals(updatedServer.getDescription(), result.getDescription());
        verify(serverRepository).save(any(Server.class));
    }

    @Test
    void updateServer_NotOwner_ThrowsException() {
        User nonOwner = new User();
        nonOwner.setId(2L);

        when(serverRepository.findById(testServer.getId())).thenReturn(Optional.of(testServer));
        when(userService.getUserById(nonOwner.getId())).thenReturn(nonOwner);

        assertThrows(RuntimeException.class,
                () -> serverService.updateServer(testServer.getId(), testServer, nonOwner.getId()));
        verify(serverRepository, never()).save(any(Server.class));
    }

    @Test
    void isUserAdmin_Success() {
        when(memberRepository.findById(testMemberId)).thenReturn(Optional.of(testMember));

        boolean result = serverService.isUserAdmin(testServer.getId(), testUser.getId());

        assertTrue(result);
    }

    @Test
    void isUserMember_Success() {
        when(memberRepository.existsById(testMemberId)).thenReturn(true);

        boolean result = serverService.isUserMember(testServer.getId(), testUser.getId());

        assertTrue(result);
    }
}
