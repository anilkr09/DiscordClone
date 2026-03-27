package com.discordclone.repository;

import com.discordclone.model.Member;
import com.discordclone.model.MemberId;
import com.discordclone.model.Server;
import com.discordclone.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, MemberId> {

    // ✅ Get all servers of a user
    @Query("SELECT m.server FROM Member m WHERE m.id.userId = :userId")
    List<Server> findServersByUserId(Long userId);

    // ✅ Get all members in a server
    @Query("SELECT m FROM Member m WHERE m.server.id = :serverId")
    List<Member> findMembersByServerId(Long serverId);

    // ✅ Get specific membership
    @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.id.userId = :userId")
    Member findMemberByServerIdAndUserId(Long serverId, Long userId);

    // ✅ Find by nickname
    @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.nickname = :nickname")
    Member findMemberByServerIdAndNickname(Long serverId, String nickname);

    // ✅ FIX: role should be ENUM, not String
    @Query("SELECT m FROM Member m WHERE m.server.id = :serverId AND m.role = :role")
    List<Member> findMembersByServerIdAndRole(Long serverId, Role role);

    // ✅ EXISTS (uses embedded id path)
    boolean existsByIdUserIdAndIdServerId(Long userId, Long serverId);
}