import api from './api';
import { Channel } from '../types/server';

class ChannelService {
    async createChannel(serverId: number, channel: Channel): Promise<Channel> {
        const response = await api.post<Channel>(`/channels/servers/${serverId}`, channel);
        return response.data;
    }

    async getServerChannels(serverId: number): Promise<Channel[]> {
        const response = await api.get<Channel[]>(`/channels/servers/${serverId}`);
        return response.data;
    }

    async updateChannel(channelId: number, channel: Channel): Promise<Channel> {
        const response = await api.put<Channel>(`/channels/${channelId}`, channel);
        return response.data;
    }

    async deleteChannel(channelId: number): Promise<void> {
        await api.delete(`/channels/${channelId}`);
    }
}

export default new ChannelService();