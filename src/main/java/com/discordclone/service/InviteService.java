package com.discordclone.service;

import com.discordclone.model.*;
import com.discordclone.repository.InviteRepository;
import com.discordclone.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final ServerService serverService;
    private final UserService userService;
    private final MemberRepository memberRepository;

    public Invite createInvite(Long serverId, int maxUses, Duration validFor, Long userId) {
        // Authorization: Only members can create invites
        if (!serverService.isUserMember(serverId, userId)) {
            throw new RuntimeException("Unauthorized: Only server members can create invites");
        }
        Server server = serverService.getServerById(serverId);

        Invite invite = Invite.builder()
                .code(UUID.randomUUID().toString().substring(0, 8))
                .server(server)
                .maxUses(maxUses)
                .uses(0)
                .expiry(LocalDateTime.now().plus(validFor))
                .build();

        return inviteRepository.save(invite);
    }

    @Transactional
    public Server joinViaInvite(String code, Long userId) {
        Invite invite = inviteRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Invalid invite code"));

        if (invite.getExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invite expired");
        }

        if (invite.getUses() >= invite.getMaxUses()) {
            throw new RuntimeException("Invite has been used too many times");
        }

        Server server = invite.getServer();
        User user = userService.getUserById(userId);

        boolean alreadyJoined = memberRepository.existsByIdUserIdAndIdServerId(userId, server.getId());
        if (alreadyJoined) {
            throw new RuntimeException("User is already a member");
        }
        MemberId memberId = new MemberId(userId, server.getId());

        Member member = Member.builder()
                .id(memberId)
                .user(user)
                .server(server)
                .nickname(user.getUsername())
                .role(Role.MEMBER)
                .build();

        memberRepository.save(member);
        invite.setUses(invite.getUses() + 1);
        inviteRepository.save(invite);

        return server;
    }
}
