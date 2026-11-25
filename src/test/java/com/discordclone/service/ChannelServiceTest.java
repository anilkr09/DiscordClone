package com.discordclone.service;

import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.Channel;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Server;
import com.discordclone.payload.ChannelPayload;
import com.discordclone.payload.UpdateChannelRequest;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.publisher.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ServerService serverService;

    @Mock
    private UserService userService;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @InjectMocks
    private ChannelService channelService;

    private Server testServer;
    private Channel testChannel;
    private Long userId;
    private Long serverId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        serverId = 1L;

        testServer = new Server();
        testServer.setId(serverId);
        testServer.setName("Test Server");

        testChannel = new Channel();
        testChannel.setId(1L);
        testChannel.setName("test-channel");
        testChannel.setDescription("Test Channel Description");
        testChannel.setType(ChannelType.TEXT);
        testChannel.setServer(testServer);
    }

    private ChannelPayload payload() {
        ChannelPayload payload = new ChannelPayload();
        payload.setName(testChannel.getName());
        payload.setDescription(testChannel.getDescription());
        return payload;
    }

    @Test
    void createChannel_Success() {
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(true);
        when(serverService.getServerById(serverId)).thenReturn(testServer);
        when(channelRepository.save(any(Channel.class))).thenReturn(testChannel);

        var result = channelService.createChannel(payload(), serverId, userId);

        assertNotNull(result);
        assertEquals(testChannel.getName(), result.getName());
        assertEquals(testChannel.getDescription(), result.getDescription());
        assertEquals(testChannel.getType(), result.getType());
        verify(channelRepository).save(any(Channel.class));
        verify(webSocketPublisher).sendToTopic(eq("/topic/servers/1/channels"), any(WsEvent.class));
    }

    @Test
    void createChannel_NotAdmin_ThrowsException() {
        when(serverService.getServerById(serverId)).thenReturn(testServer);
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> channelService.createChannel(payload(), serverId, userId));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void getChannel_Success() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));

        assertSame(testChannel, channelService.getChannel(serverId, testChannel.getId()));
    }

    @Test
    void getChannel_OfAnotherServer_IsNotFound() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));

        assertThrows(ResourceNotFoundException.class,
                () -> channelService.getChannel(999L, testChannel.getId()));
    }

    @Test
    void updateChannel_ChangesNameAndDescriptionButNotType() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(channelRepository.save(testChannel)).thenReturn(testChannel);

        Channel result = channelService.updateChannel(serverId, testChannel.getId(),
                new UpdateChannelRequest("updated-channel", "Updated Description"));

        assertEquals("updated-channel", result.getName());
        assertEquals("Updated Description", result.getDescription());
        assertEquals(ChannelType.TEXT, result.getType());
    }

    @Test
    void deleteChannel_Success() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));

        channelService.deleteChannel(serverId, testChannel.getId());

        verify(channelRepository).delete(testChannel);
    }
}
