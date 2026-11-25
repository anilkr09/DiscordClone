import { api, unwrap } from "../api/client";
import type { User } from "../types/auth";

class UserService {
  /** The signed-in user, as remembered at login. */
  getCurrentUser(): User {
    return JSON.parse(localStorage.getItem("currentUser") || "{}");
  }

  searchUsers(prefix: string): Promise<User[]> {
    return unwrap(api.GET("/api/users/search", { params: { query: { prefix } } }));
  }
}

export default new UserService();
