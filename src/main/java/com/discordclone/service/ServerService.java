package com.discordclone.service;
import com.discordclone.exception.ConflictException;
import com.discordclone.exception.NotFoundException;
import com.discordclone.exception.ValidationException;
import com.discordclone.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;

import com.discordclone.model.*;
import com.discordclone.payload.ServerPayload;
import com.discordclone.payload.UpdateServerRequest;
import com.discordclone.repository.MemberRepository;
import com.discordclone.repository.ServerRepository;
import com.discordclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
@Slf4j

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Server createServer(ServerPayload payload, Long userId) {

        // 🔹 1. Fetch Owner
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // 🔹 2. Duplicate check (because name is unique)
        if (serverRepository.existsByName(payload.getName())) {
            throw new ConflictException("Server name already exists");
        }

        // 🔹 3. Create Server
        Server server = Server.builder()
                .name(payload.getName())
                .description(payload.getDescription())
                .owner(owner)
                .build();

        Server savedServer;
        try {
            savedServer = serverRepository.save(server);
        } catch (DataIntegrityViolationException ex) {
            // handles race condition
            throw new ConflictException("Server name already exists");
        }

        log.info("Created Server: {}", savedServer);

        // 🔹 4. Create Member (Owner)
        MemberId memberId = new MemberId(userId, savedServer.getId());

        Member ownerMember = Member.builder()
                .id(memberId)
                .user(owner)
                .server(savedServer)
                .nickname(owner.getUsername())
                .role(Role.OWNER)
                .joinedAt(Instant.now())
                .build();

        memberRepository.save(ownerMember);
        log.info("Created Member: {}", ownerMember);

        return savedServer;
    }

    @Transactional(readOnly = true)
    public Server getServerById(Long id) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Server","ServerId",id));
        return server;


    }

    @Transactional(readOnly = true)
    public List<Server> getUserServers(Long userId) {
        log.info("called getuserservers");
        return memberRepository.findServersByUserId(userId);
    }

    @Transactional
    public Server addMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        MemberId memberId = new MemberId(userId, serverId);
        if (memberRepository.existsById(memberId)) {
            throw new ConflictException("User is already a member of this server");
        }
        Member member = Member.builder()
                .id(memberId)
                .user(user)
                .server(server)
                .nickname(user.getUsername())
                .role(Role.MEMBER)
                .joinedAt(Instant.now())
                .build();
        memberRepository.save(member);
        return server;
    }

    @Transactional
    public Server removeMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        if (server.getOwner().equals(user)) {
            throw new AccessDeniedException("Server owner cannot be removed");
        }

        MemberId memberId = new MemberId(userId, serverId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this server"));

        memberRepository.delete(member);
        return server;
    }

    @Transactional
    public void deleteServer(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);
        if (!server.getOwner().equals(user)) {
            throw new AccessDeniedException("Only the server owner can delete the server");
        }
        serverRepository.delete(server);
    }

    @Transactional
    public Server updateServer(Long serverId, UpdateServerRequest request, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);

        if (!server.getOwner().equals(user)) {
            throw new AccessDeniedException("Only the server owner can update the server");
        }

        server.setName(request.name());
        server.setDescription(request.description());
        return serverRepository.save(server);
    }

    @Transactional
    public Server updateRole(Long serverId, Long userId, Role newRole, Long currentUserId) {
        Server server = getServerById(serverId);
        User currentUser = userService.getUserById(currentUserId);

        if (!server.getOwner().equals(currentUser)) {
            throw new AccessDeniedException("Only the server owner can update member roles");
        }
        if (newRole == Role.OWNER || server.getOwner().getId().equals(userId)) {
            throw new ValidationException("Ownership cannot be changed through member roles");
        }

        Member member = memberRepository.findById(new MemberId(userId, serverId))
                .orElseThrow(() -> new NotFoundException("User is not a member of this server"));
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
