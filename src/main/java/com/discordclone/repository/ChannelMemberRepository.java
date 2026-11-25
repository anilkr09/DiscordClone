package com.discordclone.repository;

import com.discordclone.model.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, ChannelMember.Key> {

    @Query("SELECT m.id.userId FROM ChannelMember m WHERE m.id.channelId = :channelId")
    List<Long> findUserIds(Long channelId);
}
