import api from './api';
import { Server } from '../types/server';

class ServerService {
    async createServer(server: Server): Promise<Server> {
        const response = await api.post<Server>('/servers', server);
        return response.data;
    }

    async getUserServers(): Promise<Server[]> {
        const response = await api.get<Server[]>('/servers');
        return response.data;
    }

    async getServer(serverId: number): Promise<Server> {
        const response = await api.get<Server>(`/servers/${serverId}`);
        return response.data;
    }

    async addMember(serverId: number, userId: number): Promise<Server> {
        const response = await api.post<Server>(`/servers/${serverId}/members/${userId}`);
        return response.data;
    }

    async removeMember(serverId: number, userId: number): Promise<Server> {
        const response = await api.delete<Server>(`/servers/${serverId}/members/${userId}`);
        return response.data;
    }
}

export default new ServerService();