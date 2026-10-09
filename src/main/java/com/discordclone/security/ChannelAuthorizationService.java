package com.discordclone.security;

import com.discordclone.model.Channel;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Member;
import com.discordclone.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelAuthorizationService {

    private final MemberRepository memberRepository;

    /**
     * Checks if a user has access to a channel.
     * 
     * For server channels (TEXT, VOICE): user must be a member of the server
     * For DM channels: user must be one of the participants (parsed from dmKey)
     * 
     * @param channel the channel to check access for
     * @param userId the ID of the user requesting access
     * @return true if user has access, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(Channel channel, Long userId) {
        if (channel == null || userId == null) {
            return false;
        }

        // Server channels (TEXT, VOICE) - check server membership
        if (channel.getServer() != null) {
            return isMemberOfServer(channel.getServer().getId(), userId);
        }

        // DM channels - check if user is a participant
        if (channel.getType() == ChannelType.DM && channel.getDmKey() != null) {
            return isParticipantInDm(channel.getDmKey(), userId);
        }

        // Group DM - check if user is a participant
        if (channel.getType() == ChannelType.GROUP_DM && channel.getDmKey() != null) {
            return isParticipantInGroupDm(channel.getDmKey(), userId);
        }

        log.warn("Unknown channel type or missing server/dmKey: channelId={}, type={}", 
                 channel.getId(), channel.getType());
        return false;
    }

    /**
     * Checks if a user is a member of a server.
     */
    private boolean isMemberOfServer(Long serverId, Long userId) {
        return memberRepository.existsByIdUserIdAndIdServerId(userId, serverId);
    }

    /**
     * Checks if a user is a participant in a DM.
     * DM key format: "userId1_userId2" (sorted)
     */
    private boolean isParticipantInDm(String dmKey, Long userId) {
        try {
            String[] participants = dmKey.split("_");
            for (String participantId : participants) {
                if (Long.parseLong(participantId) == userId) {
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            log.error("Invalid DM key format: {}", dmKey, e);
        }
        return false;
    }

    /**
     * Checks if a user is a participant in a Group DM.
     * Group DM key format: "groupdm_userId1_userId2_userId3..."
     */
    private boolean isParticipantInGroupDm(String dmKey, Long userId) {
        try {
            // Remove "groupdm_" prefix if present
            String participantPart = dmKey.startsWith("groupdm_") 
                ? dmKey.substring("groupdm_".length()) 
                : dmKey;
            
            String[] participants = participantPart.split("_");
            for (String participantId : participants) {
                if (Long.parseLong(participantId) == userId) {
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            log.error("Invalid Group DM key format: {}", dmKey, e);
        }
        return false;
    }

    /**
     * Throws an exception if the user doesn't have access to the channel.
     * Use this for explicit authorization checks in controllers.
     * 
     * @param channel the channel to check
     * @param userId the user ID
     * @throws ChannelAccessDeniedException if access is denied
     */
    public void requireAccess(Channel channel, Long userId) {
        if (!hasAccess(channel, userId)) {
            log.warn("Access denied: userId={} channelId={} channelType={}", 
                     userId, channel.getId(), channel.getType());
            throw new ChannelAccessDeniedException(
                "You don't have permission to access this channel"
            );
        }
    }
}