package com.discordclone.service;

import com.discordclone.exception.ConflictException;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.discordclone.security.UserPrincipal;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(User user) {
        user.setUsername(user.getUsername().strip());
        user.setEmail(user.getEmail().strip());
        if (userRepository.existsByUsernameIgnoreCase(user.getUsername())) {
            throw new ConflictException("Username already taken");
        }

        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
//        user.setStatus(UserStatus.OFFLINE);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByName(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
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

    /**
     * Get all users
     * @return List of users
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    @Transactional(readOnly = true)
    public List<User> getUsersByPrefix(String prefix) {
        return userRepository.findByUsernameStartingWithIgnoreCase(prefix.strip());
    }

}