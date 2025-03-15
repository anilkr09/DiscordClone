import api from './api';
import { AuthResponse, LoginRequest, RefreshTokenRequest, RegisterRequest, User } from '../types/auth';
import { Client, Stomp } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { AuthProvider } from './AuthProvider.tsx';

class AuthService {
// getConnection(){
//   const accessToken = localStorage.getItem("accessToken");
// const socketUrl = `ws://localhost:8082/ws?access_token=${accessToken}`; // ✅ Using ws://

// const stompClient = new Client({
//   brokerURL: socketUrl, // ✅ Use brokerURL instead of webSocketFactory
//   connectHeaders: {
//     Authorization: "Bearer " + accessToken, // ✅ Now headers will work
//   },
//   onConnect: () => {
//     console.log("✅ Connected to WebSocket");

//     stompClient.subscribe("/topic/status", (message) => {
//       console.log("📩 Received:", message.body);
//     });
//   },
//   onStompError: (frame) => {
//     console.error("🚨 Broker error:", frame.headers["message"]);
//   },
// });

// stompClient.activate();

// //     const socketUrl = "http://localhost:8082/ws"; // Must use HTTP, not ws://

// // const stompClient = new Client({
// //   webSocketFactory: () => new SockJS(socketUrl), // Use SockJS
// //   connectHeaders: {
// //     Authorization: "Bearer " + localStorage.getItem("accessToken"), // Add JWT if required
// //   },
// //   onConnect: () => {
// //     console.log("✅ Connected to WebSocket -    --      --  -   -   -   -");

// //     stompClient.subscribe("/topic/status", (message) => {
// //       console.log("📩 Received:", message.body);
// //     });
// //   },
// //   onStompError: (frame) => {
// //     console.error("❌ STOMP Error:", frame.headers["message"]);
// //   },
// // });

// // stompClient.activate();
// }

    
    async login(credentials: LoginRequest): Promise<AuthResponse> {
        console.log("inside login service");
        const response = await api.post<AuthResponse>('/auth/login', credentials);
        console.log(response.data);
        this.setTokens(response.data);
        // setUsername(response.data.username);
        console.log("username", response.data.username);
        localStorage.setItem("username", response.data.username);
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