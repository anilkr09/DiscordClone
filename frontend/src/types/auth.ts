export interface User {
    id: number;
    username: string;
    email: string;
    avatarUrl?: string;
}

export interface LoginRequest {
    username: string;
    password: string;
}

export interface RegisterRequest {
    username: string;
    email: string;
    password: string;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    userId: number;
    username: string;
    email: string;
}

export interface RefreshTokenRequest {
    refreshToken: string;
}