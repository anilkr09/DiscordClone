import api from './api';
import { Server } from '../types/server';
import { ServerPayload } from '../types/server';

class ServerService {
    async createServer(server: ServerPayload): Promise<Server> {
        const response = await api.post<Server>('/servers', server);
        return response.data;
    }

    async getUserServers(): Promise<Server[]> {
        const response = await api.get<Server[]>('/servers');
        const result = response.data;
        console.log("server list response"+result);
        return result;
    }

    async getServer(serverId: number): Promise<Server> {
        const response = await api.get<Server>(`/servers/${serverId}`);
        return response.data;
    }

    async joinPublicServer(serverId: number): Promise<Server> {
        const response = await api.post<Server>(`/servers/${serverId}/join`);
        return response.data;
    }

    async removeMember(serverId: number, userId: number): Promise<Server> {
        const response = await api.delete<Server>(`/servers/${serverId}/members/${userId}`);
        return response.data;
    }

    async leaveServer(serverId: number): Promise<void> {
        return api.post(`/servers/${serverId}/leave`);
    }   
    async deleteServer(serverId: number): Promise<void> {
        return api.delete(`/servers/${serverId}`);
    }

    async joinServer(inviteCode: string): Promise<Server> {
        return api.post(`/servers/join`, { inviteCode });
    }   
}

export default new ServerService();