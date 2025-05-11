package com.discordclone.service;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.repository.UserRepository;
import com.discordclone.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
    }

    @Test
    void createUser_Success() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.createUser(testUser);

        assertNotNull(result);
        assertEquals(testUser.getUsername(), result.getUsername());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_UsernameExists_ThrowsException() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.createUser(testUser));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(testUser.getId(), result.getId());
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getUserById(1L));
    }

    @Test
    void getUserByUsername_Success() {
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));

        User result = userService.getUserByUsername(testUser.getUsername());

        assertNotNull(result);
        assertEquals(testUser.getUsername(), result.getUsername());
    }

    @Test
    void updateUserStatus_Success() {
        User updatedUser = new User();
        updatedUser.setId(testUser.getId());
        updatedUser.setUsername(testUser.getUsername());

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        User result = userService.updateUserStatus(testUser.getId(), UserStatus.ONLINE);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getAllUsers_Success() {
        List<User> users = Arrays.asList(testUser);
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getUsername(), result.get(0).getUsername());
    }

    @Test
    void getUsersByPrefix_Success() {
        List<User> users = Arrays.asList(testUser);
        when(userRepository.findByUsernameStartingWith(anyString())).thenReturn(users);

        List<User> result = userService.getUsersByPrefix("test");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getUsername(), result.get(0).getUsername());
    }

    @Test
    void loadUserById_Success() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        UserPrincipal result = userService.loadUserById(testUser.getId());

        assertNotNull(result);
        assertEquals(testUser.getUsername(), result.getUsername());
    }
}