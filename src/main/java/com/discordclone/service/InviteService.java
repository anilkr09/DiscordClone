package com.discordclone.service;

import com.discordclone.exception.ConflictException;
import com.discordclone.exception.ForbiddenException;
import com.discordclone.exception.NotFoundException;
import com.discordclone.exception.ValidationException;
import com.discordclone.model.*;
import com.discordclone.repository.InviteRepository;
import com.discordclone.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final ServerService serverService;
    private final UserService userService;
    private final MemberRepository memberRepository;

    public Invite createInvite(Long serverId, int maxUses, Duration validFor, Long userId, boolean fresh) {
        // Authorization: Only members can create invites
        if (!serverService.isUserMember(serverId, userId)) {
            throw new ForbiddenException("Only server members can create invites");
        }
        Server server = serverService.getServerById(serverId);

        if (!fresh) {
            // Reuse an invite that still has at least half of the requested lifetime left.
            Optional<Invite> reusable = inviteRepository.findReusable(
                    serverId, maxUses, Instant.now().plus(validFor.dividedBy(2)));
            if (reusable.isPresent()) {
                return reusable.get();
            }
        }

        Invite invite = Invite.builder()
                .code(UUID.randomUUID().toString().substring(0, 8))
                .server(server)
                .maxUses(maxUses)
                .uses(0)
                .expiry(Instant.now().plus(validFor))
                .build();

        return inviteRepository.save(invite);
    }

    @Transactional
    public Server joinViaInvite(String code, Long userId) {
        Invite invite = inviteRepository.findById(code)
                .orElseThrow(() -> new NotFoundException("Invalid invite code"));

        if (invite.getExpiry().isBefore(Instant.now())) {
            throw new ValidationException("Invite expired");
        }

        if (invite.getUses() >= invite.getMaxUses()) {
            throw new ValidationException("Invite has been used too many times");
        }

        Server server = invite.getServer();
        User user = userService.getUserById(userId);

        boolean alreadyJoined = memberRepository.existsByIdUserIdAndIdServerId(userId, server.getId());
        if (alreadyJoined) {
            throw new ConflictException("User is already a member");
        }
        MemberId memberId = new MemberId(userId, server.getId());

        Member member = Member.builder()
                .id(memberId)
                .user(user)
                .server(server)
                .nickname(user.getUsername())
                .role(Role.MEMBER)
                .build();

        if (inviteRepository.claimUse(code, Instant.now()) == 0) {
            throw new ValidationException("Invite has been used too many times");
        }
        memberRepository.save(member);

        return server;
    }
}
