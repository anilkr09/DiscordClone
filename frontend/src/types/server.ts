import { Message } from './message';

export interface Server {
    id?: number;
    name: string;
    description?: string;
    ownerId?: number;
    members?: number[];
    channels?: Channel[];
    createdAt?: string;
    updatedAt?: string;
}

export interface Channel {
    id?: number;
    name: string;
    type: ChannelType;
    serverId: number;
    messages?: Message[];
    createdAt?: string;
    updatedAt?: string;
}

export enum ChannelType {
    TEXT = 'TEXT',
    VOICE = 'VOICE'
}