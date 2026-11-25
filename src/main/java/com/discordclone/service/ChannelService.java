package com.discordclone.service;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.*;
import com.discordclone.payload.ChannelPayload;
import com.discordclone.payload.UpdateChannelRequest;
import com.discordclone.repository.ChannelMemberRepository;
import com.discordclone.repository.ChannelRepository;
import org.springframework.transaction.support.TransactionTemplate;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import com.discordclone.websocket.publisher.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ChannelService {
    
    private final ChannelRepository channelRepository;
    private final ServerService serverService;
    private final UserService userService;
    private final WebSocketPublisher webSocketPublisher;
    private final ChannelMemberRepository channelMemberRepository;
    private final TransactionTemplate transactions;
    // Authorization for admin actions (create, update, delete)
    private void checkUserIsAdmin(Long serverId, Long userId) {
        if (!serverService.isUserAdmin(serverId, userId)) {
            throw new AccessDeniedException("You are not an admin of this server");
        }
    }

    private Channel getChannelByIdInternal(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel","channelId",channelId));
    }

    @Transactional
    public ChannelDTO createChannel(ChannelPayload payload, Long serverId, Long userId) {

        Server server = serverService.getServerById(serverId);
        checkUserIsAdmin(serverId, userId);
        Channel channel = Channel.builder().name(payload.getName()).server(server).description(payload.getDescription()).build();
        channel = channelRepository.save(channel);
        ChannelDTO channelDTO = ChannelDTO.fromEntity(channel);
        announce(serverId, WsEventType.CHANNEL_CREATED, channelDTO);
        return channelDTO;
    }
    /** The conversation between two users, created on first use. */
    public Channel getOrCreateDmChannel(Long userId1, Long userId2) {
        userService.getUserById(userId1);
        userService.getUserById(userId2);
        String dmKey = DmKey.of(userId1, userId2);

        Optional<Channel> existing = channelRepository.findBydmKeyAndType(dmKey, ChannelType.DM);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return transactions.execute(status -> {
                Channel channel = channelRepository.save(
                        Channel.builder().type(ChannelType.DM).dmKey(dmKey).build());
                Stream.of(userId1, userId2).distinct()
                        .forEach(userId -> channelMemberRepository.save(new ChannelMember(channel.getId(), userId)));
                return channel;
            });
        } catch (DataIntegrityViolationException ex) {
            // Another request created it between the lookup and the insert. The failed transaction
            // is over, so this lookup runs in a fresh one.
            return channelRepository.findBydmKeyAndType(dmKey, ChannelType.DM).orElseThrow(() -> ex);
        }
    }


    /** The channel, provided it is a server channel of {@code serverId}. */
    @Transactional(readOnly = true)
    public Channel getChannel(Long serverId, Long channelId) {
        Channel channel = getChannelByIdInternal(channelId);
        if (channel.getServer() == null || !channel.getServer().getId().equals(serverId)) {
            throw new ResourceNotFoundException("Channel", "channelId", channelId);
        }
        return channel;
    }

    @Transactional(readOnly = true)
    public List<Channel> getServerChannels(Long serverId, Long userId) {
        Server server = serverService.getServerById(serverId);

        if (!serverService.isUserMember(serverId, userId)) {
            throw new AccessDeniedException("You are not a member of this server");
        }

        return channelRepository.findByServerOrderByName(server);
    }

    @Transactional
    public Channel updateChannel(Long serverId, Long channelId, UpdateChannelRequest request) {
        Channel channel = getChannel(serverId, channelId);
        channel.setName(request.name());
        channel.setDescription(request.description());
        Channel saved = channelRepository.save(channel);
        announce(serverId, WsEventType.CHANNEL_UPDATED, ChannelDTO.fromEntity(saved));
        return saved;
    }

    @Transactional
    public void deleteChannel(Long serverId, Long channelId) {
        Channel channel = getChannel(serverId, channelId);
        ChannelDTO deleted = ChannelDTO.fromEntity(channel);
        channelRepository.delete(channel);
        announce(serverId, WsEventType.CHANNEL_DELETED, deleted);
    }

    private void announce(Long serverId, WsEventType type, ChannelDTO channel) {
        webSocketPublisher.sendToTopic(WsDestinations.serverChannels(serverId), new WsEvent(type, channel));
    }
}
