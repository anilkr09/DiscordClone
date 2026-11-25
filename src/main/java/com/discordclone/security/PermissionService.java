package com.discordclone.security;

import com.discordclone.repository.FriendshipRepository;
import com.discordclone.repository.MemberRepository;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.Channel;
import com.discordclone.model.ChannelMember;
import com.discordclone.repository.ChannelMemberRepository;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Message;
import com.discordclone.model.Server;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.repository.MessageRepository;
import com.discordclone.repository.ServerRepository;
import com.discordclone.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides who may do what. HTTP endpoints call it from
 * {@code @PreAuthorize("@perm...")}; the STOMP rules and message sending call it directly.
 */
@Service("perm")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final ServerService serverService;
    private final ServerRepository serverRepository;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;

    public boolean isServerMember(Long serverId, Long userId) {
        return serverService.isUserMember(serverId, userId);
    }

    public boolean isServerAdmin(Long serverId, Long userId) {
        return serverService.isUserAdmin(serverId, userId);
    }

    public boolean isServerOwner(Long serverId, Long userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("Server", "id", serverId));
        return server.getOwner().getId().equals(userId);
    }

    /** Adding someone directly is an admin action. Everyone else joins through an invite. */
    public boolean canAddMember(Long serverId, Long userId) {
        return isServerAdmin(serverId, userId);
    }

    /** Members may remove themselves; admins and the owner may remove others. */
    public boolean canRemoveMember(Long serverId, Long targetId, Long userId) {
        return userId.equals(targetId) || isServerAdmin(serverId, userId);
    }

    public boolean isChannelMember(Long channelId, Long userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel", "id", channelId));
        return hasAccess(channel, userId);
    }

    /** DMs are decided by participation, before and instead of any server check. */
    public boolean hasAccess(Channel channel, Long userId) {
        if (channel.getType() == ChannelType.DM) {
            return channelMemberRepository.existsById(new ChannelMember.Key(channel.getId(), userId));
        }
        return channel.getServer() != null && isServerMember(channel.getServer().getId(), userId);
    }

    /** Presence is for oneself, friends and people who share a server. */
    public boolean canSeePresence(Long targetId, Long userId) {
        if (targetId.equals(userId)) {
            return true;
        }
        return friendshipRepository.areFriends(userId, targetId) || memberRepository.shareAServer(userId, targetId);
    }

    /** The author may change a message only while they can still access its channel. */
    public boolean isMessageAuthor(String messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", "id", messageId));
        return message.getSender().getId().equals(userId) && hasAccess(message.getChannel(), userId);
    }
}
