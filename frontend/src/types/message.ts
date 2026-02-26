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
    dm: boolean;
    content: string;
    channelId: string;
    receiver?:string;
    attachments?: string[];
}

export interface MessageResponse extends Message {
    edited: boolean;
    editedAt?: string;
}
