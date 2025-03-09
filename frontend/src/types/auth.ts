export interface User {
    id: number;
    username: string;
    email: string;
    avatarUrl?: string;
}
// export interface User {
//     userId: string;               // Unique user identifier
//     username: string;             // Display name
//     profilePicture?: string | null; // Optional profile picture (can be null)
//     status?: "online" | "idle" | "offline" | "typing"; // User status (optional)
// }

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
    profilePicture: string;
}

export interface RefreshTokenRequest {
    refreshToken: string;
}