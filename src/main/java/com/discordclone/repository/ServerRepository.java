package com.discordclone.repository;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {

    List<Server> findByOwner(User owner);

    @EntityGraph(attributePaths = {"owner"})
    Optional<Server> findWithOwnerById(Long id);

}