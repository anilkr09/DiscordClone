package com.discordclone.service;

import com.discordclone.model.Channel;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class ChannelService {
    
    private final ChannelRepository channelRepository;
    private final ServerService serverService;
    private final UserService userService;

    // Authorization for admin actions (create, update, delete)
    private void checkUserIsAdmin(Long serverId, Long userId) {
        if (!serverService.isUserAdmin(serverId, userId)) {
            throw new RuntimeException("Unauthorized: User is not an admin of this server");
        }
    }

    // Authorization for member actions (view)
    public boolean checkUserIsMember(Long channelId, Long userId) {
        Channel channel = getChannelByIdInternal(channelId);
        return serverService.isUserMember(channel.getServer().getId(), userId);
    }

    private Channel getChannelByIdInternal(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
    }

    @Transactional
    public Channel createChannel(Channel channel, Long serverId, Long userId) {
        checkUserIsAdmin(serverId, userId);
        Server server = serverService.getServerById(serverId);
        channel.setServer(server);
        return channelRepository.save(channel);
    }
    @Transactional
    public Channel getOrCreateDmChannel(Long userId1, Long userId2) {
        Long min = Math.min(userId1, userId2);
        Long max = Math.max(userId1, userId2);
        String channelName = "dm-" + min + "-" + max;
        System.out.println("custom channel name - "+channelName);
        return channelRepository.findByNameAndType(channelName, ChannelType.DM)
                .orElseGet(() -> {
                    User user1 = userService.getUserById(min);
                    User user2 = userService.getUserById(max);
                    Server server = serverService.getServerById(1L);
                    Channel dmChannel = new Channel();
                    dmChannel.setName(channelName);
                    dmChannel.setType(ChannelType.DM);
                    dmChannel.setServer(server);
                    return channelRepository.save(dmChannel);
                });
    }

    @Transactional(readOnly = true)
    public Channel getChannelById(Long id, Long userId) {
        Channel channel = getChannelByIdInternal(id);
        if (!serverService.isUserMember(channel.getServer().getId(), userId)) {
            throw new RuntimeException("Unauthorized: User is not a member of this server");
        }
        return channel;
    }

    @Transactional(readOnly = true)
    public List<Channel> getServerChannels(Long serverId, Long userId) {
        if (!serverService.isUserMember(serverId, userId)) {
            throw new RuntimeException("Unauthorized: User is not a member of this server");
        }
        Server server = serverService.getServerById(serverId);
        return channelRepository.findByServerOrderByName(server);
    }

    @Transactional
    public Channel updateChannel(Long channelId, Channel channelDetails, Long userId) {
        Channel channel = getChannelByIdInternal(channelId);
        checkUserIsAdmin(channel.getServer().getId(), userId);
        channel.setName(channelDetails.getName());
        channel.setDescription(channelDetails.getDescription());
        channel.setType(channelDetails.getType());
        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(Long channelId, Long userId) {
        Channel channel = getChannelByIdInternal(channelId);
        checkUserIsAdmin(channel.getServer().getId(), userId);
        channelRepository.delete(channel);
    }
}