import { api, unwrap } from "../api/client";
import type { MessagePage } from "../features/messages/cache";

/** A page of a channel's messages, newest first, older than {@code before} if given. */
export const getChannelMessages = (channelId: number, before?: string): Promise<MessagePage> =>
  unwrap(
    api.GET("/api/messages/channels/{channelId}", {
      params: { path: { channelId }, query: { before } },
    }),
  );
