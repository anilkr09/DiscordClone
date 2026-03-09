package com.discordclone.service;

import com.discordclone.model.*;
import com.discordclone.payload.ServerPayload;
import com.discordclone.repository.MemberRepository;
import com.discordclone.repository.ServerRepository;
import com.discordclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Server createServer(ServerPayload payload, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Server server = Server.builder().name(payload.getName())
                .description(payload.getDescription()).owner(owner).type(payload.getType()).build();

        Server savedServer = serverRepository.save(server);

        Member ownerMember = Member.builder()
                .userId(owner.getId())
                .serverId(savedServer.getId())
                .user(owner)
                .server(savedServer)
                .nickname(owner.getUsername())
                .role(Role.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        memberRepository.save(ownerMember);
        return savedServer;
    }

    @Transactional(readOnly = true)
    public Server getServerById(Long id) {
        System.out.println("called findfullserver");
        return serverRepository.findWithChannelsById(id)
                .orElseThrow(() -> new RuntimeException("Server not found"));



    }

    @Transactional(readOnly = true)
    public List<Server> getUserServers(Long userId) {
        return memberRepository.findServersByUserId(userId);
    }

    @Transactional
    public Server addMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        MemberId memberId = new MemberId(userId, serverId);
        if (memberRepository.existsById(memberId)) {
            throw new RuntimeException("User is already a member of this server");
        }

        Member member = Member.builder()
                .userId(userId)
                .serverId(serverId)
                .user(user)
                .server(server)
                .nickname(user.getUsername())
                .role(Role.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        memberRepository.save(member);
        return server;
    }

    @Transactional
    public Server removeMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        if (server.getOwner().equals(user)) {
            throw new RuntimeException("Server owner cannot be removed");
        }

        MemberId memberId = new MemberId(userId, serverId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this server"));

        memberRepository.delete(member);
        return server;
    }

    @Transactional
    public void deleteServer(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);
        if (!server.getOwner().equals(user)) {
            throw new RuntimeException("Only the server owner can delete the server");
        }
        serverRepository.delete(server);
    }

    @Transactional
    public Server updateServer(Long serverId, Server updatedServer, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        if (!server.getOwner().equals(user)) {
            throw new RuntimeException("Only the server owner can update the server");
        }

        server.setName(updatedServer.getName());
        server.setDescription(updatedServer.getDescription());
        return serverRepository.save(server);
    }

    @Transactional
    public Server updateRole(Long serverId, Long userId, Long roleId, Long currentUserId) {
        Server server = getServerById(serverId);
        User currentUser = userService.getUserById(currentUserId);

        if (!server.getOwner().equals(currentUser)) {
            throw new RuntimeException("Only the server owner can update member roles");
        }

        MemberId memberId = new MemberId(userId, serverId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this server"));

        // Assume Role is an enum and roleId maps to Role.ordinal()
        Role[] roles = Role.values();
        if (roleId < 0 || roleId >= roles.length) {
            throw new RuntimeException("Invalid role id");
        }
        Role newRole = roles[roleId.intValue()];

        member.setRole(newRole);
        memberRepository.save(member);

        return server;
    }

    public boolean isUserAdmin(Long serverId, Long userId) {
        MemberId memberId = new MemberId(userId, serverId);
        return memberRepository.findById(memberId)
                .map(member -> member.getRole() == Role.ADMIN || member.getRole() == Role.OWNER)
                .orElse(false);
    }

    public boolean isUserMember(Long serverId, Long userId) {
        MemberId memberId = new MemberId(userId, serverId);
        return memberRepository.existsById(memberId);
    }
}
