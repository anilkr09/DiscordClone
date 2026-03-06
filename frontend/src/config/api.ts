export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL;

export const WS_BASE_URL: string = API_BASE_URL.replace(/^http/, "ws");