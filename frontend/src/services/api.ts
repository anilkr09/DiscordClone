import axios from "axios";

const api = axios.create({
    baseURL: 'http://localhost:8082/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

let isRefreshing = false;
let refreshSubscribers: ((newToken: string) => void)[] = [];

const onRefreshed = (newToken) => {
    refreshSubscribers.forEach((callback) => callback(newToken));
    refreshSubscribers = [];
};

api.interceptors.request.use(
    async (config) => {
        let token = localStorage.getItem("accessToken");

        if (token && isTokenExpired(token)) {
            if (!isRefreshing) {
                isRefreshing = true;

                try {
                    const response = await axios.post(
                        "http://localhost:8082/api/refresh-token",
                        {},
                        { withCredentials: true }
                    );

                    const newToken = response.data.accessToken;
                    localStorage.setItem("accessToken", newToken);
                    onRefreshed(newToken);
                } catch (error) {
                    console.error("Failed to refresh token, logging out...");
                    localStorage.removeItem("accessToken");
                    localStorage.removeItem("refreshToken");
                    window.location.href = "/login";
                } finally {
                    isRefreshing = false;
                }
            }

            return new Promise((resolve) => {
                refreshSubscribers.push((newToken) => {
                    config.headers.Authorization = `Bearer ${newToken}`;
                    resolve(api(config));
                });
            });
        }

        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        return config;
    },
    (error) => Promise.reject(error)
);
const isTokenExpired = (token) => {
    if (!token) return true; // No token means it's already "expired"

    const payload = JSON.parse(atob(token.split(".")[1])); // Decode payload
    const expiry = payload.exp * 1000; // Convert to milliseconds
    return Date.now() > expiry; // If current time > expiry, token is expired
};

export default api;
