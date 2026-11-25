import { api, unwrap } from "../api/client";
import type { Server, ServerPayload } from "../types/server";

class ServerService {
  createServer(server: ServerPayload): Promise<Server> {
    return unwrap(api.POST("/api/servers", { body: server }));
  }

  getUserServers(): Promise<Server[]> {
    return unwrap(api.GET("/api/servers"));
  }

  getServer(serverId: number): Promise<Server> {
    return unwrap(api.GET("/api/servers/{serverId}", { params: { path: { serverId } } }));
  }

  /** Removes a member; with the caller's own ID, leaves the server. */
  removeMember(serverId: number, userId: number): Promise<Server> {
    return unwrap(
      api.DELETE("/api/servers/{serverId}/members/{userId}", { params: { path: { serverId, userId } } }),
    );
  }

  async deleteServer(serverId: number): Promise<void> {
    await unwrap(api.DELETE("/api/servers/{serverId}", { params: { path: { serverId } } }));
  }

  joinServer(code: string): Promise<Server> {
    return unwrap(api.POST("/api/invites/join/{code}", { params: { path: { code } } }));
  }
}

export default new ServerService();
