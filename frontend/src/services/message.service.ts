import { Client } from '@stomp/stompjs';
import api from './api';
import { Message, MessageRequest } from '../types/message';

class MessageService {
    private stompClient: Client | null = null;
    private messageHandlers: Map<number, ((message: Message) => void)[]> = new Map();

    constructor() {
        this.initializeWebSocketConnection();
    }

    private initializeWebSocketConnection() {
        // let socket = new WebSocket(`ws://localhost:8082/ws?token=${`Bearer ${localStorage.getItem('accessToken')}`}`);

        // socket.onopen = function () {
        //     console.log("Connected to WebSocket server.");
        //     console.log("Connected to server.");
        // };
        this.stompClient = new Client({
            brokerURL: 'ws://localhost:8082/ws',
            connectHeaders: {
                Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
            },
            onConnect: () => {
                window.alert('Connected to WebSocket');
            },
            onStompError: (frame) => {
                console.error('STOMP error', frame);
            }
        });

        this.stompClient.activate();
        
    }

    async sendMessage(message: MessageRequest): Promise<Message> {
        const response = await api.post<Message>(`/messages/channels/${message.channelId}`, message);
        return response.data;
    }

    async getChannelMessages(channelId: number): Promise<Message[]> {
        const response = await api.get<Message[]>(`/messages/channels/${channelId}`);
        return response.data;
    }

    async editMessage(channelId: string, messageId: string, content: string): Promise<Message> {
        const response = await api.put(`/channels/${channelId}/messages/${messageId}`, { content });
        return response.data;
    }

    async deleteMessage(channelId: string, messageId: string): Promise<void> {
        await api.delete(`/channels/${channelId}/messages/${messageId}`);
    }

    subscribeToChannel(channelId: number, callback: (message: Message) => void) {
        if (!this.stompClient?.connected) {
            console.error('WebSocket not connected');
            return;
        }

        if (!this.messageHandlers.has(channelId)) {
            this.messageHandlers.set(channelId, []);
            this.stompClient.subscribe(`/topic/channels/${channelId}`, (message) => {
                const messageData: Message = JSON.parse(message.body);
                const handlers = this.messageHandlers.get(channelId) || [];
                handlers.forEach(handler => handler(messageData));
            });
        }

        const handlers = this.messageHandlers.get(channelId) || [];
        handlers.push(callback);
        this.messageHandlers.set(channelId, handlers);
    }

    unsubscribeFromChannel(channelId: number, callback: (message: Message) => void) {
        const handlers = this.messageHandlers.get(channelId) || [];
        const index = handlers.indexOf(callback);
        if (index > -1) {
            handlers.splice(index, 1);
        }
        this.messageHandlers.set(channelId, handlers);
    }

    disconnect() {
        if (this.stompClient?.connected) {
            this.stompClient.deactivate();
        }
    }

    subscribeToChannelMessages(channelId: string, callback: (message: Message) => void) {
        const ws = new WebSocket(`ws://${window.location.host}/ws/chat/${channelId}`);
        
        ws.onmessage = (event) => {
            const message = JSON.parse(event.data);
            callback(message);
        };

        return () => {
            ws.close();
        };
    }
}

export default new MessageService();