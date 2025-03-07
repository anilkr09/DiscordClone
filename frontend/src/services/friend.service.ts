import api from './api';
import { Friend, FriendRequest, AddFriendRequest, AddFriendResponse, FriendshipStatus } from '../types/friend';

class FriendService {
  /**
   * Get all friends for the current user
   */
  async getFriends(): Promise<Friend[]> {
    const response = await api.get('/friends');
    return response.data;
  }

  /**
   * Get friends filtered by status
   */
  async getFriendsByStatus(status: FriendshipStatus): Promise<Friend[]> {
    const response = await api.get(`/friends?status=${status}`);
    return response.data;
  }

  /**
   * Send a friend request to a user by username
   */
  async addFriend(username: string): Promise<AddFriendResponse> {
    try {
      const request: AddFriendRequest = { username };
      const response = await api.post('/api/friends/request', request);
      return {
        success: true,
        message: 'Friend request sent successfully',
        friendRequest: response.data
      };
    } catch (error: any) {
      return {
        success: false,
        message: error.response?.data?.message || 'Failed to send friend request'
      };
    }
  }

  /**
   * Get all pending friend requests
   */
  async getFriendRequests(): Promise<FriendRequest[]> {
    const response = await api.get('/api/friends/requests');
    return response.data;
  }

  /**
   * Accept a friend request
   */
  async acceptFriendRequest(requestId: number): Promise<Friend> {
    const response = await api.put(`/api/friends/accept/${requestId}`);
    return response.data;
  }

  /**
   * Reject a friend request
   */
  async rejectFriendRequest(requestId: number): Promise<void> {
    await api.put(`/api/friends/reject/${requestId}`);
  }

  /**
   * Remove a friend
   */
  async removeFriend(friendId: number): Promise<void> {
    await api.delete(`/api/friends/${friendId}`);
  }

  /**
   * Block a user
   */
  async blockUser(userId: number): Promise<void> {
    await api.post(`/api/friends/block/${userId}`);
  }

  /**
   * Unblock a user
   */
  async unblockUser(userId: number): Promise<void> {
    await api.delete(`/api/friends/block/${userId}`);
  }
}

export default new FriendService(); 