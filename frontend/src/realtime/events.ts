import toast from "react-hot-toast";
import type { QueryClient } from "@tanstack/react-query";
import type { Schemas } from "../api/client";
import type { Channel } from "../types/server";
import type { Message } from "../types/message";
import type { Friend, FriendRequest } from "../types/friend";
import type { UserStatus } from "../types/status";
import { receiveMessage, removeMessage, updateMessage } from "../features/messages/cache";

/** Every frame the server pushes. The type names are generated from the backend's enum. */
export type ServerEvent = { version: number } & (
  | { type: "MESSAGE_CREATED" | "MESSAGE_UPDATED"; payload: Message }
  | { type: "MESSAGE_DELETED"; payload: { id: string; channelId: number } }
  | { type: "PRESENCE_CHANGED"; payload: { userId: number; status: UserStatus } }
  | { type: "FRIEND_REQUEST_RECEIVED"; payload: { request: FriendRequest } }
  | { type: "FRIEND_ACCEPTED"; payload: { friend: Friend; requestId: number } }
  | { type: "FRIEND_REJECTED"; payload: { id: number } }
  | { type: "FRIEND_REMOVED"; payload: { friendId: number } }
  | { type: "CHANNEL_CREATED" | "CHANNEL_UPDATED" | "CHANNEL_DELETED"; payload: Channel }
  | { type: "ERROR"; payload: { status: number; message: string } }
);

// Fails to compile if the backend adds or renames a type that is not handled above.
type Unhandled = Exclude<Schemas["WsEventType"], ServerEvent["type"]>;
const allTypesHandled: Unhandled extends never ? true : Unhandled = true;
void allTypesHandled;

const PROTOCOL_VERSION = 1;

const addById = <T extends { id: number }>(items: T[] | undefined, item: T): T[] =>
  items?.some((existing) => existing.id === item.id) ? items : [...(items ?? []), item];

const withoutId = <T extends { id: number }>(items: T[] | undefined, id: number): T[] | undefined =>
  items?.filter((item) => item.id !== id);

/** Applies a pushed event to the cached server state. */
export function applyEvent(queryClient: QueryClient, body: unknown): void {
  const event = body as ServerEvent;
  if (event.version !== PROTOCOL_VERSION) {
    console.warn("Ignoring an event of an unknown version", event);
    return;
  }
  switch (event.type) {
    case "MESSAGE_CREATED":
      return receiveMessage(queryClient, event.payload);
    case "MESSAGE_UPDATED":
      return updateMessage(queryClient, event.payload);
    case "MESSAGE_DELETED":
      return removeMessage(queryClient, String(event.payload.channelId), event.payload.id);
    case "PRESENCE_CHANGED": {
      const { userId, status } = event.payload;
      queryClient.setQueryData<Record<number, UserStatus>>(["presence"], (old) =>
        old ? { ...old, [userId]: status } : old,
      );
      return;
    }
    case "FRIEND_REQUEST_RECEIVED":
      queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], (old) =>
        addById(old, event.payload.request),
      );
      return;
    case "FRIEND_ACCEPTED": {
      const { friend, requestId } = event.payload;
      queryClient.setQueryData<Friend[]>(["friends"], (old) => addById(old, friend));
      for (const key of ["incomingRequests", "outgoingRequests"]) {
        queryClient.setQueryData<FriendRequest[]>([key], (old) => withoutId(old, requestId));
      }
      return;
    }
    case "FRIEND_REJECTED":
      queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], (old) =>
        withoutId(old, event.payload.id),
      );
      return;
    case "FRIEND_REMOVED":
      queryClient.setQueryData<Friend[]>(["friends"], (old) => withoutId(old, event.payload.friendId));
      return;
    case "CHANNEL_CREATED":
    case "CHANNEL_UPDATED":
    case "CHANNEL_DELETED": {
      const channel = event.payload;
      queryClient.setQueryData<Channel[]>(["channels", channel.serverId], (old) => {
        if (!old) return old;
        if (event.type === "CHANNEL_DELETED") return withoutId(old, channel.id);
        if (event.type === "CHANNEL_CREATED") return addById(old, channel);
        return old.map((existing) => (existing.id === channel.id ? channel : existing));
      });
      return;
    }
    case "ERROR":
      toast.error(event.payload.message, { id: event.payload.message });
      return;
  }
}
