package com.discordclone.repository;

import com.discordclone.model.Channel;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByServer(Server server);
    List<Channel> findByServerOrderByName(Server server);

    Optional<Channel> findByNameAndType(String channelName, ChannelType channelType);
} 