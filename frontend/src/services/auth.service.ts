import api from './api';
import axios from "axios";

import { AuthResponse, LoginRequest, RefreshTokenRequest, RegisterRequest, User } from '../types/auth';
import { Client, Stomp } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { AuthProvider } from './AuthProvider.tsx';

class AuthService {


     authApi = axios.create({
    baseURL: "https://localhost:8082/api",
    headers: {
        "Content-Type": "application/json",
    },
});

    
    async login(credentials: LoginRequest): Promise<AuthResponse> {
        console.log("inside login service");
        const response = await this.authApi.post<AuthResponse>('/auth/login', credentials);
        console.log(response.data);
        this.setTokens(response.data);
        // setUsername(response.data.username);
        console.log("username", response.data.username);
        localStorage.setItem("username", response.data.username);
        localStorage.setItem("id", response.data.userId.toString());
        localStorage.setItem("accessToken", response.data.accessToken);
        return response.data;
    }

    async register(userData: RegisterRequest): Promise<AuthResponse> {
        const response = await this.authApi.post<AuthResponse>('/auth/register', userData);
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