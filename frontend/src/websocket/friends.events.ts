import { QueryClient } from "@tanstack/react-query";
import { Friend, FriendRequest } from "../types/friend";
type FriendAcceptedPayload = {
  friend: Friend;
  requestId: number;
};

export function handleFriendAccepted(
  queryClient: QueryClient,
  payload: FriendAcceptedPayload
) {
  const { friend, requestId } = payload;

  // 1️⃣ Add to friends list (idempotent)
  queryClient.setQueryData<Friend[]>(["friends"], old => {
    if (!old) return [friend];
    if (old.some(f => f.id === friend.id)) return old;
    return [...old, friend];
  });

  // 2️⃣ Remove from outgoing requests (sender side)
  queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );

  // 3️⃣ Remove from incoming requests (receiver side)
  queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old =>
    old?.filter(r => r.id !== requestId)
  );
}
