package com.discordclone.repository;

import com.discordclone.model.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, String> {
    // ...existing code...
}
