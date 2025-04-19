

package com.discordclone.repository;

import com.discordclone.model.DmChannel;
import com.discordclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DmChannelRepository extends JpaRepository<DmChannel, Long> {

    Optional<DmChannel> findByUser1AndUser2(User user1, User user2);
}
