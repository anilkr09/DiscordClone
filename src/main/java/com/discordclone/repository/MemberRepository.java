package com.discordclone.repository;

import com.discordclone.model.Channel;
import com.discordclone.model.Member;
import com.discordclone.model.MemberId;
import com.discordclone.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
    public interface MemberRepository extends JpaRepository<Member, MemberId> {


        @Query("SELECT m.server FROM Member m WHERE m.userId = :userId")
        List<Server> findServersByUserId(@Param("userId") Long userId);
        @Query("SELECT m FROM Member m WHERE m.server.id = :serverId")
        List<Member> findMembersByServerId(@Param("serverId") Long serverId);
        @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.userId = :userId")
        Member findMemberByServerIdAndUserId(@Param("serverId") Long serverId, @Param("userId") Long userId);
        @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.nickname = :nickname")
        Member findMemberByServerIdAndNickname(@Param("serverId") Long serverId, @Param("nickname") String nickname);
        @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.role = :role")
        List<Member> findMembersByServerIdAndRole(@Param("serverId") Long serverId, @Param("role") String role);
        boolean existsByUserIdAndServerId(Long userId, Long id);
    }



