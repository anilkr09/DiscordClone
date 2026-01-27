import { QueryClient } from "@tanstack/react-query";
import { Friend, FriendRequest } from "../types/friend";

/* =========================
   Payload Types
========================= */

export type FriendAcceptedPayload = {
  friend: Friend;
  requestId: number;
};

export type FriendRequestPayload = {
  request: FriendRequest;
};

export type FriendRequestActionPayload = {
  id: number;
};

export type FriendRemovedPayload = {
  friendId: number;
};

/* =========================
   Event Handlers
========================= */

/**
 * ✅ Friend Accepted
 * - Add friend to friends list
 * - Remove request from both incoming & outgoing (safe for both sides)
 */
export function handleFriendAccepted(
  queryClient: QueryClient,
  payload: FriendAcceptedPayload
) {
  const { friend, requestId } = payload;

  // Add to friends list (idempotent)
  queryClient.setQueryData<Friend[]>(["friends"], old => {
    if (!old) return [friend];
    if (old.some(f => f.id === friend.id)) return old;
    return [...old, friend];
  });

  // Remove from incoming requests
  queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );

  // Remove from outgoing requests
  queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );
}

/**
 * ❌ Friend Rejected (receiver side)
 */
export function handleFriendRejected(
  queryClient: QueryClient,
  payload: FriendRequestActionPayload
) {
  const { id: requestId } = payload;
  console.log("Handling Friend Rejected for Request ID:", requestId); 
  queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );
}

/**
 * ↩️ Friend Request Cancelled (sender side)
 */
export function handleFriendRequestCancelled(
  queryClient: QueryClient,
  payload: FriendRequestActionPayload
) {
  const { id: requestId } = payload;

  queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );
}

/**
 * 🗑️ Friend Removed (Unfriend)
 */
export function handleFriendRemoved(
  queryClient: QueryClient,
  payload: FriendRemovedPayload
) {
  const { friendId } = payload;

  queryClient.setQueryData<Friend[]>(["friends"], old =>
    old?.filter(f => f.id !== friendId)
  );
}

/**
 * 📩 Friend Request Received
 */
export function handleFriendRequestReceived(
  queryClient: QueryClient,
  payload: FriendRequestPayload
) {
  const { request } = payload;
console.log("Received Friend Request Payload:", payload);
  queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old => {
    if (!old) return [request];
    if (old.some(r => r.id === request.id)) return old;
    return [...old, request];
  });
  console.log(
  "Cache after set:",
  queryClient.getQueryData(["incomingRequests"])
);
}

/**
 * 📤 Friend Request Sent
 */
export function handleFriendRequestSent(
  queryClient: QueryClient,
  payload: FriendRequestPayload
) {
  const { request } = payload;

  queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], old => {
    if (!old) return [request];
    if (old.some(r => r.id === request.id)) return old;
    return [...old, request];
  });
}

/* =========================
   Central Event Router
   (WebSocket / STOMP)
========================= */

export function handleFriendEvent(
  queryClient: QueryClient,
  type: string,
  payload: any
) {
  switch (type) {
    case "FRIEND_ACCEPTED":
      return handleFriendAccepted(queryClient, payload);

    case "FRIEND_REJECTED":
      return handleFriendRejected(queryClient, payload);

    case "FRIEND_REQUEST_CANCELLED":
      return handleFriendRequestCancelled(queryClient, payload);

    case "FRIEND_REMOVED":
      return handleFriendRemoved(queryClient, payload);

    case "FRIEND_REQUEST_RECEIVED":
      return handleFriendRequestReceived(queryClient, payload);

    case "FRIEND_REQUEST_SENT":
      return handleFriendRequestSent(queryClient, payload);

    default:
      // Unknown / future-safe
      return;
  }
}
