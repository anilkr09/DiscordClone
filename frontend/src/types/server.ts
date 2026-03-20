import { User } from './auth';
import { Message } from './message';

export interface Server {
    id: number;
    name: string;
    description?: string;
    owser: User;
    createdAt?: string;
}
export interface ServerPayload {
    name:string;
    description:string;
    type:ServerType;
    icon?: File;
}
export interface ChannelPayload {
    name: string;
    type: ChannelType;
    description:string;

    
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
    VOICE = 'VOICE',
    DM = 'DM'
}
export enum ServerType{
    PUBLIC='PUBLIC',
    PRIVATE='PRIVATE'
}