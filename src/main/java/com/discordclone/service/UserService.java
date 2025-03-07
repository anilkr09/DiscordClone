package com.discordclone.service;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.discordclone.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email is already registered");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(UserStatus.OFFLINE);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User updateUserStatus(Long userId, UserStatus status) {
        User user = getUserById(userId);
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Find a user by ID
     * @param id User ID
     * @return User object
     */
    public User findById(Long id) {
        return getUserById(id);
    }
    
    /**
     * Find a user by username
     * @param username Username
     * @return User object
     */
    public User findByUsername(String username) {
        return getUserByUsername(username);
    }
    
    /**
     * Load user by ID for authentication
     * @param id User ID
     * @return UserPrincipal object
     */
    public UserPrincipal loadUserById(Long id) {
        User user = getUserById(id);
        return UserPrincipal.create(user);
    }
} 