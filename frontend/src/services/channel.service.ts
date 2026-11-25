import { api, unwrap } from "../api/client";
import type { Channel, ChannelPayload } from "../types/server";

class ChannelService {
  createChannel({ serverId, channel }: { serverId: number; channel: ChannelPayload }): Promise<Channel> {
    return unwrap(api.POST("/api/channels/{serverId}", { params: { path: { serverId } }, body: channel }));
  }

  getChannels(serverId: number): Promise<Channel[]> {
    return unwrap(api.GET("/api/channels/{serverId}", { params: { path: { serverId } } }));
  }

  updateChannel(serverId: number, channelId: number, channel: ChannelPayload): Promise<Channel> {
    return unwrap(
      api.PUT("/api/channels/{serverId}/{channelId}", {
        params: { path: { serverId, channelId } },
        body: channel,
      }),
    );
  }

  async deleteChannel(serverId: number, channelId: number): Promise<void> {
    await unwrap(
      api.DELETE("/api/channels/{serverId}/{channelId}", { params: { path: { serverId, channelId } } }),
    );
  }
}

export default new ChannelService();
