package com.discordclone.service;

import com.discordclone.dto.DeletedMessage;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import com.discordclone.dto.MessagePage;
import com.discordclone.dto.MessageResp;
import com.discordclone.repository.ChannelMemberRepository;
import com.discordclone.exception.ForbiddenException;
import com.discordclone.exception.ResourceNotFoundException;
import com.discordclone.model.ChannelType;
import com.discordclone.model.Friendship;
import com.discordclone.model.FriendshipStatus;
import com.discordclone.repository.FriendshipRepository;
import com.discordclone.security.PermissionService;
import com.discordclone.model.Channel;
import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.repository.MessageRepository;
import com.discordclone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private MessagePersistenceService persistenceService;

    @Mock
    private MessageEventPublisher eventPublisher;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private PermissionService permissions;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @InjectMocks
    private MessageService messageService;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Captor
    private ArgumentCaptor<MessageResponse> messageResponseCaptor;

    private User testUser;
    private Channel testChannel;
    private Message testMessage;
    private MessageRequest testMessageRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");

        testChannel = new Channel();
        testChannel.setId(1L);
        testChannel.setName("testChannel");

        testMessage = new Message();
        testMessage.setId("message-1");
        testMessage.setSender(testUser);
        testMessage.setChannel(testChannel);
        testMessage.setContent("Test message");
        testMessage.setTimestamp(Instant.now());

        testMessageRequest = new MessageRequest();
        testMessageRequest.setChannelId(1L);
        testMessageRequest.setContent("Test message");
    }

    @Test
    void sendMessage_Success() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(permissions.hasAccess(testChannel, testUser.getId())).thenReturn(true);
        testMessageRequest.setDm(true);
        testMessageRequest.setReceiver("someone-else");

        Message result = messageService.sendMessage(testMessageRequest, testUser.getId());

        verify(eventPublisher).publish(messageCaptor.capture(), messageResponseCaptor.capture(),
                eq(testMessageRequest), eq(testUser));
        assertSame(result, messageCaptor.getValue());
        assertEquals("Test message", result.getContent());
        assertNotNull(result.getId());
        assertEquals(result.getId(), messageResponseCaptor.getValue().getId());
        assertEquals(testChannel.getId(), messageResponseCaptor.getValue().getChannelId());
        assertEquals(testUser.getId(), messageResponseCaptor.getValue().getAuthor().getId());
        // The client cannot turn a server channel message into a direct delivery.
        assertFalse(testMessageRequest.isDm());
        assertNull(testMessageRequest.getReceiver());
    }

    @Test
    void sendMessage_UserNotFound_ThrowsException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> messageService.sendMessage(testMessageRequest, 999L));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendMessage_ChannelNotFound_ThrowsException() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(channelRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> messageService.sendMessage(testMessageRequest, testUser.getId()));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendMessage_WithoutChannelAccess_IsRefused() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));

        assertThrows(ForbiddenException.class,
                () -> messageService.sendMessage(testMessageRequest, testUser.getId()));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendMessage_InDm_DerivesTheRecipientFromTheChannel() {
        User friend = User.builder().id(2L).username("MixedCase").build();
        Channel dm = Channel.builder().id(5L).type(ChannelType.DM).dmKey("dm-1-2").build();
        testMessageRequest.setChannelId(5L);
        testMessageRequest.setReceiver("an-attacker-chosen-name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(channelRepository.findById(5L)).thenReturn(Optional.of(dm));
        when(permissions.hasAccess(dm, 1L)).thenReturn(true);
        when(channelMemberRepository.findUserIds(5L)).thenReturn(java.util.List.of(1L, 2L));

        messageService.sendMessage(testMessageRequest, 1L);

        assertTrue(testMessageRequest.isDm());
        assertEquals("MixedCase", testMessageRequest.getReceiver());
    }

    @Test
    void sendMessage_InDm_WhenBlocked_IsRefused() {
        User friend = User.builder().id(2L).username("friend").build();
        Channel dm = Channel.builder().id(5L).type(ChannelType.DM).dmKey("dm-1-2").build();
        Friendship block = new Friendship();
        block.setStatus(FriendshipStatus.BLOCKED);
        testMessageRequest.setChannelId(5L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(channelRepository.findById(5L)).thenReturn(Optional.of(dm));
        when(permissions.hasAccess(dm, 1L)).thenReturn(true);
        when(channelMemberRepository.findUserIds(5L)).thenReturn(java.util.List.of(1L, 2L));
        when(friendshipRepository.findBySenderAndReceiver(testUser, friend)).thenReturn(Optional.empty());
        when(friendshipRepository.findBySenderAndReceiver(friend, testUser)).thenReturn(Optional.of(block));

        assertThrows(ForbiddenException.class,
                () -> messageService.sendMessage(testMessageRequest, 1L));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getChannelMessages_ReportsWhetherOlderMessagesRemain() {
        when(messageRepository.findLatest(eq(1L), any(Limit.class))).thenReturn(Arrays.asList(testMessage, testMessage));

        MessagePage page = messageService.getChannelMessages(1L, null, 1);

        assertEquals(1, page.content().size());
        assertTrue(page.hasMore());
    }

    @Test
    void editMessage_ChangesOnlyTheContent() {
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(testMessage));
        when(messageRepository.save(testMessage)).thenReturn(testMessage);

        MessageResp result = messageService.editMessage("message-1", "edited");

        assertEquals("edited", result.getContent());
        assertTrue(result.isEdited());
        assertSame(testUser, testMessage.getSender());
        assertSame(testChannel, testMessage.getChannel());
        verify(events).publishEvent(ChatBroadcast.toTopic("/topic/channels/1/messages",
                new WsEvent(WsEventType.MESSAGE_UPDATED, result)));
    }

    @Test
    void deleteMessage_Success() {
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(testMessage));

        messageService.deleteMessage("message-1");

        verify(messageRepository).delete(testMessage);
        verify(events).publishEvent(ChatBroadcast.toTopic("/topic/channels/1/messages",
                new WsEvent(WsEventType.MESSAGE_DELETED, new DeletedMessage("message-1", 1L))));
    }
}
