package com.discordclone.controller;

import com.discordclone.payload.UserDTO;
import com.discordclone.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.getUserById(id)));
    }

    /**
     * Get user by username
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserDTO> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.getUserByUsername(username)));
    }

    /**
     * Get all users
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers().stream().map(UserDTO::fromEntity).toList());
    }

    /**
     * Get users by prefix
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> getUsersByPrefix(@RequestParam String prefix) {
        return ResponseEntity.ok(userService.getUsersByPrefix(prefix).stream().map(UserDTO::fromEntity).toList());
    }
}
