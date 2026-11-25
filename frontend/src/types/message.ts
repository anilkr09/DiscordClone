import type { Schemas } from "../api/client";

/** A chat message, as returned by history requests and pushed over the WebSocket. */
export interface Message {
  id: string;
  content: string;
  timestamp: string;
  channelId: number | string;
  author: Schemas["Author"] & { avatarUrl?: string };
  edited?: boolean;
}

/** Sent over STOMP to /app/chat.send, so it is not part of the REST contract. */
export interface MessageRequest {
  content: string;
  channelId: number;
}
