export enum FriendshipStatus {
  PENDING = 'PENDING',
  ACCEPTED = 'ACCEPTED',
  REJECTED = 'REJECTED',
  BLOCKED = 'BLOCKED'
}

export interface Friend {
  id: number;
  username: string;
  email?: string;
  avatarUrl?: string;
  status: string;
  friendshipId: number;
  friendshipStatus: FriendshipStatus;
}

export interface FriendRequest {
  id: number;
  senderId: number;
  senderUsername: string;
  senderAvatarUrl?: string;
  receiverId: number;
  receiverUsername: string;
  receiverAvatarUrl?: string;
  status: FriendshipStatus;
  createdAt: string;
}

export interface AddFriendRequest {
  username: string;
}

export interface AddFriendResponse {
  success: boolean;
  message: string;
  friendRequest?: FriendRequest;
} 