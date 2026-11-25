import type { Schemas } from "../api/client";

export type User = Schemas["UserDTO"];
export type LoginRequest = Schemas["LoginRequest"];
export type RegisterRequest = Schemas["RegistrationRequest"];
export type AuthResponse = Schemas["JwtAuthResponse"];
export type RefreshTokenRequest = Schemas["RefreshTokenRequest"];
