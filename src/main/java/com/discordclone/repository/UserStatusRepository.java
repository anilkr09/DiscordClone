package com.discordclone.repository;

import com.discordclone.model.UserStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserStatusRepository extends JpaRepository<UserStatusEntity, String> {
    List<UserStatusEntity> findByLastActivityBefore(LocalDateTime time);
    List<UserStatusEntity> findByStatusExpiresAtBefore(LocalDateTime time);
} 