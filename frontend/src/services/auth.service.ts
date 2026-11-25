import { api, unwrap } from "../api/client";
import { session } from "../api/session";
import type { LoginRequest, RegisterRequest } from "../types/auth";

class AuthService {
  async login(credentials: LoginRequest): Promise<void> {
    session.start(await unwrap(api.POST("/api/auth/login", { body: credentials })));
  }

  async register(userData: RegisterRequest): Promise<void> {
    session.start(await unwrap(api.POST("/api/auth/register", { body: userData })));
  }
}

export default new AuthService();
