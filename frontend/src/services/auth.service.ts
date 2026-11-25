import { api, unwrap } from "../api/client";
import type { AuthResponse, LoginRequest, RefreshTokenRequest, RegisterRequest, User } from "../types/auth";

class AuthService {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    return this.remember(await unwrap(api.POST("/api/auth/login", { body: credentials })));
  }

  async register(userData: RegisterRequest): Promise<AuthResponse> {
    return this.remember(await unwrap(api.POST("/api/auth/register", { body: userData })));
  }

  async refreshToken(request: RefreshTokenRequest): Promise<AuthResponse> {
    return this.remember(await unwrap(api.POST("/api/auth/refresh", { body: request })));
  }

  private remember(response: AuthResponse): AuthResponse {
    localStorage.setItem("accessToken", response.accessToken);
    localStorage.setItem("refreshToken", response.refreshToken);
    localStorage.setItem("username", response.username);
    localStorage.setItem("id", String(response.userId));
    const user: User = { id: response.userId, username: response.username, avatarUrl: "" };
    localStorage.setItem("currentUser", JSON.stringify(user));
    return response;
  }

  logout() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("currentUser");
  }

  getCurrentUser(): User {
    return JSON.parse(localStorage.getItem("currentUser") || "{}");
  }
}

export default new AuthService();
