import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import FriendService from "../services/friend.service";
import { Friend, FriendRequest } from "../types/friend";
import { AddFriendResponse } from "../types/friend";
export function useFriends() {
  const queryClient = useQueryClient();

  // 1️⃣ Friends list
  const friends = useQuery({
    queryKey: ["friends"],
    queryFn: () => FriendService.getFriends(),
    initialData: [] as Friend[]
  });

  // 2️⃣ Incoming friend requests (I received)
  const incomingRequests = useQuery({
    queryKey: ["incomingRequests"],
    queryFn: () => FriendService.getFriendRequests(),
    initialData: [] as FriendRequest[]
  });

  // 3️⃣ Outgoing friend requests (I sent)
  const outgoingRequests = useQuery({
    queryKey: ["outgoingRequests"],
    queryFn: () => FriendService.getOutgoingFriendRequests(),
       initialData: [] as FriendRequest[]
  });

   // 4️⃣ Send friend request (ADD FRIEND)
const addFriend = useMutation<AddFriendResponse, Error, string>({
  mutationFn: (username: string) =>
    FriendService.addFriend(username),

  onSuccess: (response) => {
    const { success, friendRequest } = response;

    // ⛔ hard stop — TS now knows friendRequest is NOT optional below
    if (!success || !friendRequest) return;

    queryClient.setQueryData<FriendRequest[]>(["outgoingRequests"], old => {
      if (!old) return [friendRequest];

      if (old.some(r => r.id === friendRequest.id)) {
        return old;
      }

      return [...old, friendRequest];
    });
  }
});

  // 4️⃣ Accept incoming friend request (USER ACTION)
  const acceptFriend = useMutation({
    mutationFn: (requestId: number) =>
      FriendService.acceptFriendRequest(requestId),

    onSuccess: (newFriend, requestId) => {
      // ✅ add to friends (idempotent)
      queryClient.setQueryData<Friend[]>(["friends"], old => {
        if (!old) return [newFriend];
        if (old.some(f => f.id === newFriend.id)) return old;
        return [...old, newFriend];
      });

      // ✅ remove from incoming requests using requestId
      queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old =>
        old?.filter(r => r.id !== requestId)
      );
    }
  });
  // 2️⃣ Reject incoming friend request
  const rejectFriend = useMutation({
    mutationFn: (requestId: number) =>
      FriendService.rejectFriendRequest(requestId),

    onSuccess: (_, requestId) => {
      // remove from incoming requests
      queryClient.setQueryData<FriendRequest[]>(["incomingRequests"], old =>{

      if (!old) return [];
        return old.filter(r => r.id !== requestId);

      }
      );
    }
  });

  // 3️⃣ Remove friend
  const removeFriend = useMutation({
    mutationFn: (friendId: number) =>
      FriendService.removeFriend(friendId),

    onSuccess: (_, friendId) => {
      // remove from friends list
      queryClient.setQueryData<Friend[]>(["friends"], old =>
        {

      if (!old) return [];
        return old.filter(f => f.id !== friendId);
        }
      );
    }
  });

  /* ---------------- Public API ---------------- */

  return {
    friends,
    incomingRequests,
    outgoingRequests,
    addFriend,
    acceptFriend,
    rejectFriend,
    removeFriend
  };
}
