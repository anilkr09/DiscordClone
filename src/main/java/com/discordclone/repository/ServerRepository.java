package com.discordclone.repository;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findByMembersContaining(User user);
    List<Server> findByOwner(User owner);
} 