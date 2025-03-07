package com.discordclone.service.impl;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository , PasswordEncoder passwordEncoder) {
        super(userRepository,passwordEncoder);
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    @Transactional(readOnly = true)
    public UserPrincipal loadUserById(Long id) {
        User user = findById(id);
        return UserPrincipal.create(user);
    }
}