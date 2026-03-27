import api from './api';
import { Channel, ChannelPayload } from '../types/server';

class ChannelService {
    async createChannel(data: { serverId: number; channel: ChannelPayload }): Promise<Channel> {
        const { serverId, channel } = data;
        const response = await api.post<Channel>(`/channels/${serverId}`, channel);
        return response.data;
    }

    async getChannels(serverId: number): Promise<Channel[]> {
        const response = await api.get<Channel[]>(`/channels/${serverId}`);
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