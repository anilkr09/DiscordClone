package com.discordclone.repository;

import com.discordclone.model.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, String> {

    /** Takes one use if any is left, atomically. Returns 0 when the invite is spent or expired. */
    @Modifying
    @Query("UPDATE Invite i SET i.uses = i.uses + 1 "
            + "WHERE i.code = :code AND i.uses < i.maxUses AND i.expiry > :now")
    int claimUse(String code, Instant now);

    @Query("SELECT i FROM Invite i WHERE i.server.id = :serverId AND i.maxUses = :maxUses "
            + "AND i.uses < i.maxUses AND i.expiry > :validUntil ORDER BY i.expiry DESC LIMIT 1")
    Optional<Invite> findReusable(Long serverId, int maxUses, Instant validUntil);
}
