import { api, unwrap } from "../api/client";
import type { AddFriendResponse, Friend, FriendRequest } from "../types/friend";

class FriendService {
  getFriends(): Promise<Friend[]> {
    return unwrap(api.GET("/api/friends"));
  }

  /** Reports failure in the result rather than throwing, so the form can show the reason. */
  async addFriend(username: string): Promise<AddFriendResponse> {
    try {
      const friendRequest = await unwrap(api.POST("/api/friends/request", { body: { username } }));
      return { success: true, message: "Friend request sent successfully", friendRequest };
    } catch (error) {
      return {
        success: false,
        message: error instanceof Error ? error.message : "Failed to send friend request",
      };
    }
  }

  getFriendRequests(): Promise<FriendRequest[]> {
    return unwrap(api.GET("/api/friends/requests"));
  }

  getOutgoingFriendRequests(): Promise<FriendRequest[]> {
    return unwrap(api.GET("/api/friends/requests/outgoing"));
  }

  acceptFriendRequest(requestId: number): Promise<Friend> {
    return unwrap(api.PUT("/api/friends/accept/{requestId}", { params: { path: { requestId } } }));
  }

  async rejectFriendRequest(requestId: number): Promise<void> {
    await unwrap(api.PUT("/api/friends/reject/{requestId}", { params: { path: { requestId } } }));
  }

  async removeFriend(friendId: number): Promise<void> {
    await unwrap(api.DELETE("/api/friends/{friendId}", { params: { path: { friendId } } }));
  }

  async blockUser(targetId: number): Promise<void> {
    await unwrap(api.POST("/api/friends/block/{targetId}", { params: { path: { targetId } } }));
  }

  async unblockUser(targetId: number): Promise<void> {
    await unwrap(api.DELETE("/api/friends/block/{targetId}", { params: { path: { targetId } } }));
  }

  checkFriendship(friendId: number): Promise<boolean> {
    return unwrap(api.GET("/api/friends/check/{friendId}", { params: { path: { friendId } } }));
  }
}

export default new FriendService();
