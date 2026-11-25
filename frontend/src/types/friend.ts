import type { Schemas } from "../api/client";

export type Friend = Schemas["FriendDTO"];
export type FriendRequest = Schemas["FriendshipDTO"];
export type FriendshipStatus = FriendRequest["status"];

export interface AddFriendResponse {
  success: boolean;
  message: string;
  friendRequest?: FriendRequest;
}
