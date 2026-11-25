package com.discordclone.repository;

import com.discordclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    List<User> findAllByUsernameIgnoreCase(String username);

    List<User> findByUsernameStartingWithIgnoreCase(String prefix);

    Optional<User> findByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Looks a user up by name, ignoring case and surrounding space. An exact match wins, which keeps
     * accounts created before names became case-insensitive reachable.
     */
    default Optional<User> findByName(String username) {
        String name = username.strip();
        return findByUsername(name).or(() -> {
            List<User> matches = findAllByUsernameIgnoreCase(name);
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        });
    }
}
