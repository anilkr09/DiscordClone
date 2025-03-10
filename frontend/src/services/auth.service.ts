import api from './api';
import { AuthResponse, LoginRequest, RefreshTokenRequest, RegisterRequest, User } from '../types/auth';

class AuthService {
    async login(credentials: LoginRequest): Promise<AuthResponse> {
        console.log("inside login service");
        const response = await api.post<AuthResponse>('/auth/login', credentials);
        console.log(response.data);
        this.setTokens(response.data);
        return response.data;
    }

    async register(userData: RegisterRequest): Promise<AuthResponse> {
        const response = await api.post<AuthResponse>('/auth/register', userData);
        this.setTokens(response.data);
        return response.data;
    }

    async refreshToken(request: RefreshTokenRequest): Promise<AuthResponse> {
        const response = await api.post<AuthResponse>('/auth/refresh', request);
        this.setTokens(response.data);
        return response.data;
    }

    private setTokens(authResponse: AuthResponse) {
        localStorage.setItem("accessToken", authResponse.accessToken);
        localStorage.setItem("refreshToken", authResponse.refreshToken);

        // Save the current user details
        const user = {
            id: authResponse.userId, // Ensure the response contains this
            username: authResponse.username,
            email: "",
            avatarUrl: ""
        };
        localStorage.setItem("currentUser", JSON.stringify(user));
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