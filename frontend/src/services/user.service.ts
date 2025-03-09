import api from './api';
import { User } from '../types/auth';
import authService from './auth.service';

class UserService {
  private currentUser: User | null = null;

  /**
   * Get the current user's information
   */
  getCurrentUser(): User {
    return JSON.parse(localStorage.getItem("currentUser") || "{}");
}

  /**
   * Search users by username
   */
  async searchUsers(query: string): Promise<User[]> {
    try {
      const response = await api.get<User[]>(`/users/search?prefix=${encodeURIComponent(query)}`);
      return response.data;
      console.log("users -"+response.data);
    } catch (error: any) {
      console.error('Error searching users:', error);
      throw new Error(error.response?.data?.message || 'Failed to search users');
    }
  }

  /**
   * Clear the cached current user
   */
  clearCurrentUser(): void {
    this.currentUser = null;
  }
}

export default new UserService(); 