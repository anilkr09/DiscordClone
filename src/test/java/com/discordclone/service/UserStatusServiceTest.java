package com.discordclone.service;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.repository.UserRepository;
import com.discordclone.repository.UserStatusRepository;
import com.discordclone.service.impl.UserStatusServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusRepository userStatusRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UserStatusServiceImpl userStatusService;

    @Captor
    private ArgumentCaptor<UserStatusEntity> statusEntityCaptor;

    private User testUser;
    private UserStatusEntity testStatusEntity;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testStatusEntity = new UserStatusEntity();
        testStatusEntity.setId(1L);
        testStatusEntity.setCurrentStatus(UserStatus.ONLINE);
    }

    @Test
    void updateUserStatus_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userStatusRepository.findById("1")).thenReturn(Optional.of(testStatusEntity));
        when(userStatusRepository.save(any(UserStatusEntity.class))).thenReturn(testStatusEntity);

        // Act
        User result = userStatusService.updateUserStatus(1L, UserStatus.IDLE);

        // Assert
        verify(userStatusRepository).save(statusEntityCaptor.capture());
        UserStatusEntity savedEntity = statusEntityCaptor.getValue();
        assertEquals(UserStatus.IDLE, savedEntity.getCurrentStatus());
        assertNotNull(result);
        verify(messagingTemplate).convertAndSend(eq("/topic/status"), any(java.util.Map.class));
    }

    @Test
    void updateUserStatus_UserNotFound() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            userStatusService.updateUserStatus(1L, UserStatus.IDLE)
        );
    }

    @Test
    void getAllUserStatus_Success() {
        // Arrange
        List<UserStatusEntity> statusList = Arrays.asList(testStatusEntity);
        when(userStatusRepository.findAll()).thenReturn(statusList);

        // Act
        List<UserStatusEntity> result = userStatusService.getAllUserStatus();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(UserStatus.ONLINE, result.get(0).getCurrentStatus());
    }

    @Test
    void updateCustomStatus_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userStatusRepository.findById("1")).thenReturn(Optional.of(testStatusEntity));
        when(userStatusRepository.save(any(UserStatusEntity.class))).thenReturn(testStatusEntity);

        // Act
        User result = userStatusService.updateCustomStatus(1L, UserStatus.DO_NOT_DISTURB);

        // Assert
        verify(userStatusRepository).save(statusEntityCaptor.capture());
        UserStatusEntity savedEntity = statusEntityCaptor.getValue();
        assertEquals(UserStatus.DO_NOT_DISTURB, savedEntity.getCustomStatus());
        assertNotNull(savedEntity.getStatusExpiresAt());
        assertNotNull(result);
        verify(messagingTemplate).convertAndSend(eq("/topic/status"), any(java.util.Map.class));
    }

    @Test
    void getUserStatus_Found() {
        // Arrange
        when(userStatusRepository.findById("1")).thenReturn(Optional.of(testStatusEntity));

        // Act
        UserStatus result = userStatusService.getUserStatus(1L);

        // Assert
        assertEquals(UserStatus.ONLINE, result);
    }

    @Test
    void getUserStatus_NotFound() {
        // Arrange
        when(userStatusRepository.findById("1")).thenReturn(Optional.empty());

        // Act
        UserStatus result = userStatusService.getUserStatus(1L);

        // Assert
        assertEquals(UserStatus.OFFLINE, result);
    }

    @Test
    void clearCustomStatus_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userStatusRepository.findById("1")).thenReturn(Optional.of(testStatusEntity));
        when(userStatusRepository.save(any(UserStatusEntity.class))).thenReturn(testStatusEntity);

        // Act
        User result = userStatusService.clearCustomStatus(1L);

        // Assert
        verify(userStatusRepository).save(statusEntityCaptor.capture());
        UserStatusEntity savedEntity = statusEntityCaptor.getValue();
        assertNull(savedEntity.getCustomStatus());
        assertNull(savedEntity.getStatusExpiresAt());
        assertNotNull(result);
    }

    @Test
    void broadcastStatusChange_Success() {
        // Act
        userStatusService.broadcastStatusChange(1L, UserStatus.ONLINE);

        // Assert
        verify(messagingTemplate).convertAndSend(eq("/topic/status"), any(java.util.Map.class));
    }
}