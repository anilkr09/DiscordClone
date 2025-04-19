import { Client } from '@stomp/stompjs';
import api from './api';
import { Message, MessageRequest } from '../types/message';
import { useWebSocketTopic } from './WebSocketProvider';
import { useCallback, useRef } from 'react';

// Standalone functions
export const sendMessageToChannel = async (message: MessageRequest): Promise<Message> => {
    try {
        const response = await api.post(`/messages/channels/${message.channelId}`, message);
        return response.data;
    } catch (error) {
        throw new Error('Failed to send message');
    }
};

export const getChannelMessages = async (channelId: number): Promise<Message[]> => {
    try {
        const response = await api.get<Message[]>(`/messages/channels/${channelId}`);
        return response.data;
    } catch (error) {
        throw new Error('Failed to fetch channel messages');
    }
};

export const editMessage = async (channelId: string, messageId: string, content: string): Promise<Message> => {
    try {
        const response = await api.put(`/channels/${channelId}/messages/${messageId}`, { content });
        return response.data;
    } catch (error) {
        throw new Error('Failed to edit message');
    }
};

export const deleteMessage = async (channelId: string, messageId: string): Promise<void> => {
    try {
        await api.delete(`/channels/${channelId}/messages/${messageId}`);
    } catch (error) {
        throw new Error('Failed to delete message');
    }
};

// WebSocket related functions
export const createWebSocketConnection = (channelId: string): WebSocket => {
    return new WebSocket(`ws://${window.location.host}/ws/chat/${channelId}`);
};

export const subscribeToChannelMessages = (channelId: string, callback: (message: Message) => void) => {
    const ws = createWebSocketConnection(channelId);
    
    ws.onmessage = (event) => {
        const message = JSON.parse(event.data);
        callback(message);
    };

    return () => {
        ws.close();
    };
};

// Hook for real-time messaging
export const useMessageService = () => {
    const { sendMessage: sendWebSocketMessage, connected } = useWebSocketTopic("/app/chat");
    const stompClientRef = useRef<Client | null>(null);
    const messageHandlersRef = useRef<Map<number, ((message: Message) => void)[]>>(new Map());

    const sendMessage = useCallback(async (message: MessageRequest): Promise<Message> => {
        if (connected) {
            sendWebSocketMessage(message);
            return message as Message;
        }
        // Fallback to REST API if WebSocket is not connected
        return sendMessageToChannel(message);
    }, [connected, sendWebSocketMessage]);

    const subscribeToChannel = useCallback((channelId: number, callback: (message: Message) => void) => {
        if (!stompClientRef.current?.connected) {
            console.error('WebSocket not connected');
            return;
        }

        if (!messageHandlersRef.current.has(channelId)) {
            messageHandlersRef.current.set(channelId, []);
            stompClientRef.current.subscribe(`/topic/channels/${channelId}`, (message) => {
                const messageData: Message = JSON.parse(message.body);
                const handlers = messageHandlersRef.current.get(channelId) || [];
                handlers.forEach(handler => handler(messageData));
            });
        }

        const handlers = messageHandlersRef.current.get(channelId) || [];
        handlers.push(callback);
        messageHandlersRef.current.set(channelId, handlers);
    }, []);

    const unsubscribeFromChannel = useCallback((channelId: number, callback: (message: Message) => void) => {
        const handlers = messageHandlersRef.current.get(channelId) || [];
        const index = handlers.indexOf(callback);
        if (index > -1) {
            handlers.splice(index, 1);
        }
        messageHandlersRef.current.set(channelId, handlers);
    }, []);

    const disconnect = useCallback(() => {
        if (stompClientRef.current?.connected) {
            stompClientRef.current.deactivate();
        }
    }, []);

    return {
        sendMessage,
        getChannelMessages,
        editMessage,
        deleteMessage,
        subscribeToChannel,
        unsubscribeFromChannel,
        disconnect,
        subscribeToChannelMessages,
        isConnected: connected
    };
};

// Export message service methods
export const MessageService = {
    sendMessage: sendMessageToChannel,
    getChannelMessages,
    editMessage,
    deleteMessage,
    subscribeToChannelMessages,
    createWebSocketConnection
};

export default MessageService;