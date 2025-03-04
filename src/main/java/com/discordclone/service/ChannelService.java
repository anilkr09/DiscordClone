package com.discordclone.service;

import com.discordclone.model.Channel;
import com.discordclone.model.Server;
import com.discordclone.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService {
    
    private final ChannelRepository channelRepository;
    private final ServerService serverService;

    @Transactional
    public Channel createChannel(Channel channel, Long serverId) {
        Server server = serverService.getServerById(serverId);
        channel.setServer(server);
        return channelRepository.save(channel);
    }

    @Transactional(readOnly = true)
    public Channel getChannelById(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
    }

    @Transactional(readOnly = true)
    public List<Channel> getServerChannels(Long serverId) {
        Server server = serverService.getServerById(serverId);
        return channelRepository.findByServerOrderByName(server);
    }

    @Transactional
    public Channel updateChannel(Long channelId, Channel channelDetails) {
        Channel channel = getChannelById(channelId);
        channel.setName(channelDetails.getName());
        channel.setDescription(channelDetails.getDescription());
        channel.setType(channelDetails.getType());
        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = getChannelById(channelId);
        channelRepository.delete(channel);
    }
} 