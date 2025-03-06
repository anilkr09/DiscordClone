import { User } from './auth';

export interface Message {
    id: string;
    content: string;
    timestamp: string;
    author: User;
    channelId: string;
    attachments?: string[];
}

export interface MessageRequest {
    content: string;
    channelId: string;
    attachments?: string[];
}

export interface MessageResponse extends Message {
    edited: boolean;
    editedAt?: string;
}