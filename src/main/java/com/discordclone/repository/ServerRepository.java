package com.discordclone.repository;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findByMembersContaining(User user);
    List<Server> findByOwner(User owner);
    @Query("""
    SELECT s FROM Server s
    LEFT JOIN FETCH s.owner
    LEFT JOIN FETCH s.members m
    LEFT JOIN FETCH s.channels c
    WHERE s.id = :serverId
""")

    Optional<Server> findFullServerById(@Param("serverId") Long serverId);
    @EntityGraph(attributePaths = {"channels"})
    Optional<Server> findWithChannelsById(Long id);
    @Query("SELECT s FROM Server s " +
            "LEFT JOIN FETCH s.members m " +
            "LEFT JOIN FETCH m.user " +
            "LEFT JOIN FETCH s.channels c " +
            "LEFT JOIN FETCH s.owner " +
            "WHERE s.id = :id")
    Optional<Server> findByIdWithDetails(@Param("id") Long id);
    @Query("SELECT DISTINCT s FROM Server s " +
            "LEFT JOIN FETCH s.members m " +
            "LEFT JOIN FETCH m.user " +
            "LEFT JOIN FETCH s.owner " +
            "WHERE s.id = :serverId")
    Optional<Server> findServerWithMembers(@Param("serverId") Long serverId);

    @Query("SELECT DISTINCT s FROM Server s " +
            "LEFT JOIN FETCH s.channels c " +
            "LEFT JOIN FETCH s.owner " +
            "WHERE s.id = :serverId")
    Optional<Server> findServerWithChannels(@Param("serverId") Long serverId);
} 