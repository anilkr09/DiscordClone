import api from './api';
import { AuthResponse, LoginRequest, RefreshTokenRequest, RegisterRequest } from '../types/auth';

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

    logout(): void {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
    }

    private setTokens(auth: AuthResponse): void {
        localStorage.setItem('accessToken', auth.accessToken);
        localStorage.setItem('refreshToken', auth.refreshToken);
    }

    isAuthenticated(): boolean {
        return !!localStorage.getItem('accessToken');
    }
}

export default new AuthService();