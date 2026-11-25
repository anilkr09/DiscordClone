package com.discordclone.service;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.*;
import com.discordclone.payload.ChannelPayload;
import com.discordclone.payload.UpdateChannelRequest;
import com.discordclone.repository.ChannelRepository;
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

@Service
@RequiredArgsConstructor
public class ChannelService {
    
    private final ChannelRepository channelRepository;
    private final ServerService serverService;
    private final UserService userService;
    private final WebSocketPublisher webSocketPublisher;
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
        webSocketPublisher.sendToTopic( WsDestinations.CHANNELS,new WsEvent(
                WsEventType.CHANNEL_CREATED,channelDTO )
        );
        return channelDTO;
    }
    @Transactional
    public Channel getOrCreateDmChannel(Long userId1, Long userId2) {

        User user1 = userService.getUserById(userId1);
        User user2 = userService.getUserById(userId2);
        String dmKey = DmKey.of(userId1, userId2);

        Optional<Channel> existing =
                channelRepository.findBydmKeyAndType(dmKey, ChannelType.DM);

        if (existing.isPresent()) {
            return existing.get();
        }

        try {

            Server server = serverService.getServerById(1L);

            Channel dmChannel = new Channel();
            dmChannel.setType(ChannelType.DM);
            dmChannel.setDmKey(dmKey);
            dmChannel.setServer(server);

            return channelRepository.save(dmChannel);

        } catch (DataIntegrityViolationException ex) {

            // Another thread created it between find and save
            return channelRepository
                    .findBydmKeyAndType(dmKey, ChannelType.DM)
                    .orElseThrow(() -> ex);
        }
    }


    /** The channel, provided it is a server channel of {@code serverId}. */
    @Transactional(readOnly = true)
    public Channel getChannel(Long serverId, Long channelId) {
        Channel channel = getChannelByIdInternal(channelId);
        if (channel.getType() == ChannelType.DM || channel.getServer() == null
                || !channel.getServer().getId().equals(serverId)) {
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

        // DM channels are attached to a server for now, but they are not part of it.
        return channelRepository.findByServerOrderByName(server).stream()
                .filter(channel -> channel.getType() != ChannelType.DM)
                .toList();
    }

    @Transactional
    public Channel updateChannel(Long serverId, Long channelId, UpdateChannelRequest request) {
        Channel channel = getChannel(serverId, channelId);
        channel.setName(request.name());
        channel.setDescription(request.description());
        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(Long serverId, Long channelId) {
        channelRepository.delete(getChannel(serverId, channelId));
    }
}
