import { Client } from "@stomp/stompjs";
import { WS_BASE_URL } from "../config/api";
import { session } from "../api/session";
import { SubscriptionManager } from "./subscriptionManager";

/** The application's single WebSocket connection. */
const client = new Client({
  brokerURL: `${WS_BASE_URL}/ws`,
  // Runs before every attempt, so a reconnect uses a current token.
  beforeConnect: async () => {
    const token = await session.freshToken();
    client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
  },
  onConnect: () => realtime.connected(),
  onWebSocketClose: () => realtime.disconnected(),
  onStompError: (frame) => console.warn("The server refused a frame:", frame.headers.message),
});

export const realtime = new SubscriptionManager(client);

export const startRealtime = () => client.activate();

export const stopRealtime = async () => {
  realtime.disconnected();
  await client.deactivate();
};
