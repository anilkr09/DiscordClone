package com.discordclone.service;

import com.discordclone.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    @Captor
    private ArgumentCaptor<String> destinationCaptor;

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
        testMessage.setId(1L);
        testMessage.setSender(testUser);
        testMessage.setChannel(testChannel);
        testMessage.setContent("Test message");
        testMessage.setTimestamp(LocalDateTime.now());

        testMessageRequest = new MessageRequest();
        testMessageRequest.setChannelId(1L);
        testMessageRequest.setContent("Test message");
    }

    @Test
    void sendMessage_Success() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(channelRepository.findById(testChannel.getId())).thenReturn(Optional.of(testChannel));
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);

        Message result = messageService.sendMessage(testMessageRequest, testUser.getId());

        assertNotNull(result);
        assertEquals(testMessage.getContent(), result.getContent());
        verify(messagingTemplate).convertAndSend(anyString(), any(MessageResponse.class));
    }

    @Test
    void sendMessage_UserNotFound_ThrowsException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> messageService.sendMessage(testMessageRequest, 999L));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessage_ChannelNotFound_ThrowsException() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(channelRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> messageService.sendMessage(testMessageRequest, testUser.getId()));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void getChannelMessages_Success() {
        Page<Message> messagePage = new PageImpl<>(Arrays.asList(testMessage));
        when(messageRepository.findByChannelOrderByTimestampDesc(any(Channel.class), any(Pageable.class)))
                .thenReturn(messagePage);

        Page<Message> result = messageService.getChannelMessages(testChannel, Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testMessage.getContent(), result.getContent().get(0).getContent());
    }

    @Test
    void editMessage_Success() {
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);

        Message result = messageService.editMessage(testMessage);

        assertNotNull(result);
        assertEquals(testMessage.getContent(), result.getContent());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/channels/" + testMessage.getChannel().getId() + "/messages"),
                eq(testMessage)
        );
    }

    @Test
    void deleteMessage_Success() {
        doNothing().when(messageRepository).delete(any(Message.class));

        messageService.deleteMessage(testMessage);

        verify(messageRepository).delete(testMessage);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/channels/" + testMessage.getChannel().getId() + "/messages/delete"),
                eq(testMessage.getId())
        );
    }
}