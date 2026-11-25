package com.discordclone.service;

import com.discordclone.constants.PresenceKeys;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusRepository userStatusRepository;

    @Mock
    private FriendshipService friendService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private LastSeenRecorder lastSeenRecorder;

    @InjectMocks
    private UserStatusServiceImpl userStatusService;

    @Captor
    private ArgumentCaptor<UserStatusEntity> statusEntityCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setUsername("testuser");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private void redisHas(String key, String value) {
        lenient().when(valueOps.get(key)).thenReturn(value);
    }

    private static String millisAgo(long millis) {
        return String.valueOf(System.currentTimeMillis() - millis);
    }

    private void verifyBroadcast(UserStatus status) {
        verify(messagingTemplate).convertAndSend(eq("/topic/status"),
                eq((Object) Map.of("userId", USER_ID, "status", status)));
    }

    @Test
    void getUserStatus_returnsCachedStatus() {
        redisHas(PresenceKeys.status(USER_ID), "IDLE");

        assertEquals(UserStatus.IDLE, userStatusService.getUserStatus(USER_ID));
    }

    @Test
    void getUserStatus_withoutHeartbeat_isOffline() {
        assertEquals(UserStatus.OFFLINE, userStatusService.getUserStatus(USER_ID));
    }

    @Test
    void getUserStatus_withRecentActivity_isOnline() {
        redisHas(PresenceKeys.heartbeat(USER_ID), millisAgo(1_000));
        redisHas(PresenceKeys.lastActivity(USER_ID), millisAgo(1_000));

        assertEquals(UserStatus.ONLINE, userStatusService.getUserStatus(USER_ID));
    }

    @Test
    void getUserStatus_withStaleActivity_isIdle() {
        redisHas(PresenceKeys.heartbeat(USER_ID), millisAgo(1_000));
        redisHas(PresenceKeys.lastActivity(USER_ID), millisAgo(60_000));

        assertEquals(UserStatus.IDLE, userStatusService.getUserStatus(USER_ID));
    }

    @Test
    void handleHeartbeat_storesHeartbeatAndBroadcastsChange() {
        userStatusService.handleHeartbeat(USER_ID);

        verify(valueOps).set(eq(PresenceKeys.heartbeat(USER_ID)), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOps).set(eq(PresenceKeys.status(USER_ID)), anyString(), eq(Duration.ofSeconds(60)));
        verify(messagingTemplate).convertAndSend(eq("/topic/status"), any(Object.class));
    }

    @Test
    void handleHeartbeat_withUnchangedStatus_doesNotBroadcast() {
        redisHas(PresenceKeys.status(USER_ID), "OFFLINE");

        userStatusService.handleHeartbeat(USER_ID);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleActivity_isThrottled() {
        redisHas(PresenceKeys.lastActivity(USER_ID), millisAgo(1_000));

        userStatusService.handleActivity(USER_ID);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void getFriendsStatus_usesCachedValuesAndResolvesMissingOnes() {
        when(friendService.getFriendIds(USER_ID)).thenReturn(List.of(2L, 3L));
        when(valueOps.multiGet(List.of(PresenceKeys.status(2L), PresenceKeys.status(3L))))
                .thenReturn(Arrays.asList("ONLINE", null));

        List<StatusResponse> result = userStatusService.getFriendsStatus(USER_ID);

        assertEquals(2, result.size());
        assertEquals("ONLINE", result.get(0).getStatus());
        assertEquals("OFFLINE", result.get(1).getStatus());
    }

    @Test
    void updateCustomStatus_storesInRedisAndDatabase() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userStatusRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        User result = userStatusService.updateCustomStatus(USER_ID, UserStatus.DO_NOT_DISTURB);

        assertSame(testUser, result);
        verify(valueOps).set(PresenceKeys.custom(USER_ID), "DO_NOT_DISTURB", Duration.ofHours(24));
        verify(userStatusRepository).save(statusEntityCaptor.capture());
        assertEquals(UserStatus.DO_NOT_DISTURB, statusEntityCaptor.getValue().getCustomStatus());
        assertNotNull(statusEntityCaptor.getValue().getStatusExpiresAt());
    }

    @Test
    void updateCustomStatus_userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userStatusService.updateCustomStatus(USER_ID, UserStatus.IDLE));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void clearCustomStatus_removesOverride() {
        UserStatusEntity entity = new UserStatusEntity();
        entity.setCustomStatus(UserStatus.IDLE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userStatusRepository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        userStatusService.clearCustomStatus(USER_ID);

        verify(redisTemplate).delete(PresenceKeys.custom(USER_ID));
        verify(userStatusRepository).save(entity);
        assertNull(entity.getCustomStatus());
        assertNull(entity.getStatusExpiresAt());
    }

    @Test
    void broadcastStatusChange_publishesToStatusTopic() {
        userStatusService.broadcastStatusChange(USER_ID, UserStatus.ONLINE);

        verifyBroadcast(UserStatus.ONLINE);
    }
}
