import { api, unwrap } from "../api/client";
import type { Message } from "../types/message";

/** The latest messages of a channel, oldest first. */
export const getChannelMessages = async (channelId: number): Promise<Message[]> => {
  const page = await unwrap(
    api.GET("/api/messages/channels/{channelId}", { params: { path: { channelId } } }),
  );
  return [...page.content].reverse();
};

export default { getChannelMessages };
