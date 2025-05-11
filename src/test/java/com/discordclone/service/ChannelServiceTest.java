package com.discordclone.service;

import com.discordclone.model.Channel;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Server;
import com.discordclone.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ServerService serverService;

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

    @Test
    void createChannel_Success() {
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(true);
        when(serverService.getServerById(serverId)).thenReturn(testServer);
        when(channelRepository.save(any(Channel.class))).thenReturn(testChannel);

        Channel result = channelService.createChannel(testChannel, serverId, userId);

        assertNotNull(result);
        assertEquals(testChannel.getName(), result.getName());
        assertEquals(testChannel.getDescription(), result.getDescription());
        assertEquals(testChannel.getType(), result.getType());
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    void createChannel_NotAdmin_ThrowsException() {
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> channelService.createChannel(testChannel, serverId, userId));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void getChannelById_Success() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(serverService.isUserMember(serverId, userId)).thenReturn(true);

        Channel result = channelService.getChannelById(testChannel.getId(), userId);

        assertNotNull(result);
        assertEquals(testChannel.getId(), result.getId());
        assertEquals(testChannel.getName(), result.getName());
    }

    @Test
    void getChannelById_NotMember_ThrowsException() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(serverService.isUserMember(serverId, userId)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> channelService.getChannelById(testChannel.getId(), userId));
    }

    @Test
    void getServerChannels_Success() {
        List<Channel> channels = Arrays.asList(testChannel);
        when(serverService.isUserMember(serverId, userId)).thenReturn(true);
        when(serverService.getServerById(serverId)).thenReturn(testServer);
        when(channelRepository.findByServerOrderByName(testServer)).thenReturn(channels);

        List<Channel> result = channelService.getServerChannels(serverId, userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testChannel.getName(), result.get(0).getName());
    }

    @Test
    void updateChannel_Success() {
        Channel updatedChannel = new Channel();
        updatedChannel.setName("updated-channel");
        updatedChannel.setDescription("Updated Description");
        updatedChannel.setType(ChannelType.ANNOUNCEMENT);

        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(true);
        when(channelRepository.save(any(Channel.class))).thenReturn(updatedChannel);

        Channel result = channelService.updateChannel(testChannel.getId(), updatedChannel, userId);

        assertNotNull(result);
        assertEquals(updatedChannel.getName(), result.getName());
        assertEquals(updatedChannel.getDescription(), result.getDescription());
        assertEquals(updatedChannel.getType(), result.getType());
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    void deleteChannel_Success() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(true);
        doNothing().when(channelRepository).delete(any(Channel.class));

        channelService.deleteChannel(testChannel.getId(), userId);

        verify(channelRepository).delete(testChannel);
    }

    @Test
    void deleteChannel_NotAdmin_ThrowsException() {
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(serverService.isUserAdmin(serverId, userId)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> channelService.deleteChannel(testChannel.getId(), userId));
        verify(channelRepository, never()).delete(any(Channel.class));
    }
}
