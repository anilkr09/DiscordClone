import api from './api';
import { Friend, FriendRequest, AddFriendRequest, AddFriendResponse, FriendshipStatus } from '../types/friend';

class FriendService {
  /**
   * Get all friends for the current user
   */
  async getFriends(): Promise<Friend[]> {
    try {
      const response = await api.get('/friends');
      return response.data;
    } catch (error: any) {
      console.error('Error fetching friends:', error);
      throw new Error(error.response?.data?.message || 'Failed to fetch friends');
    }
  }

  /**
   * Get friends filtered by status
   */
  async getFriendsByStatus(status: FriendshipStatus): Promise<Friend[]> {
    try {
      const response = await api.get(`/friends?status=${status}`);
      return response.data;
    } catch (error: any) {
      console.error(`Error fetching friends with status ${status}:`, error);
      throw new Error(error.response?.data?.message || 'Failed to fetch friends by status');
    }
  }

  /**
   * Send a friend request to a user by username
   */
  async addFriend(username: string): Promise<AddFriendResponse> {
    try {
      const request: AddFriendRequest = { username };
      const response = await api.post('/friends/request', request);
      return {
        success: true,
        message: 'Friend request sent successfully',
        friendRequest: response.data
      };
    } catch (error: any) {
      console.error('Error sending friend request:', error);
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
    try {
      const response = await api.get('/friends/requests');
      return response.data;
    } catch (error: any) {
      console.error('Error fetching friend requests:', error);
      throw new Error(error.response?.data?.message || 'Failed to fetch friend requests');
    }
  }

  /**
   * Accept a friend request
   */
  async acceptFriendRequest(requestId: number): Promise<Friend> {
    try {
      const response = await api.put(`/friends/accept/${requestId}`);
      return response.data;
    } catch (error: any) {
      console.error('Error accepting friend request:', error);
      throw new Error(error.response?.data?.message || 'Failed to accept friend request');
    }
  }

  /**
   * Reject a friend request
   */
  async rejectFriendRequest(requestId: number): Promise<void> {
    try {
      await api.put(`/friends/reject/${requestId}`);
    } catch (error: any) {
      console.error('Error rejecting friend request:', error);
      throw new Error(error.response?.data?.message || 'Failed to reject friend request');
    }
  }

  /**
   * Remove a friend
   */
  async removeFriend(friendId: number): Promise<void> {
    try {
      await api.delete(`/friends/${friendId}`);
    } catch (error: any) {
      console.error('Error removing friend:', error);
      throw new Error(error.response?.data?.message || 'Failed to remove friend');
    }
  }

  /**
   * Block a user
   */
  async blockUser(userId: number): Promise<void> {
    try {
      await api.post(`/friends/block/${userId}`);
    } catch (error: any) {
      console.error('Error blocking user:', error);
      throw new Error(error.response?.data?.message || 'Failed to block user');
    }
  }

  /**
   * Unblock a user
   */
  async unblockUser(userId: number): Promise<void> {
    try {
      await api.delete(`/friends/block/${userId}`);
    } catch (error: any) {
      console.error('Error unblocking user:', error);
      throw new Error(error.response?.data?.message || 'Failed to unblock user');
    }
  }

  /**
   * Check if users are friends
   */
  async checkFriendship(friendId: number): Promise<boolean> {
    try {
      const response = await api.get(`/friends/check/${friendId}`);
      return response.data;
    } catch (error: any) {
      console.error('Error checking friendship:', error);
      throw new Error(error.response?.data?.message || 'Failed to check friendship status');
    }
  }
}

export default new FriendService(); 