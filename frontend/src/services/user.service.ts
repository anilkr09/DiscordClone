import { api, unwrap } from "../api/client";
import { session } from "../api/session";
import type { User } from "../types/auth";

class UserService {
  /** The signed-in user. */
  getCurrentUser(): User {
    const user = session.user();
    return { id: user?.id ?? 0, username: user?.username ?? "", avatarUrl: "" };
  }

  searchUsers(prefix: string): Promise<User[]> {
    return unwrap(api.GET("/api/users/search", { params: { query: { prefix } } }));
  }
}

export default new UserService();
