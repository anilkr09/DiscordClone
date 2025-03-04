package com.discordclone.repository;

import com.discordclone.model.Channel;
import com.discordclone.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByServer(Server server);
    List<Channel> findByServerOrderByName(Server server);
} 