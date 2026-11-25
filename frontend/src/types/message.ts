import type { Schemas } from "../api/client";

/** A chat message, as returned by history requests and pushed over the WebSocket. */
export interface Message {
  id: string;
  content: string;
  timestamp: string;
  channelId: number | string;
  author: Schemas["Author"] & { avatarUrl?: string };
  edited?: boolean;
  /** Set by the sender's client, and echoed by the server. */
  nonce?: string;
  /** Shown while waiting for the server to deliver it. */
  pending?: boolean;
  failed?: boolean;
}
