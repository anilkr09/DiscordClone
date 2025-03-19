import React, { createContext, useEffect, useState, ReactNode, useContext } from "react";
import { Client } from "@stomp/stompjs";
import {useAuth} from "./AuthProvider.tsx";
// Define the context type
interface SocketContextType {
    stompClient: Client | null;
    isConnected: boolean;
}

// Create the context with a default value
export const SocketContext = createContext<SocketContextType | null>(null);

interface WebSocketProviderProps {
    children: ReactNode;
}

export const WebSocketProvider: React.FC<WebSocketProviderProps> = ({ children }) => {
    const [stompClient, setStompClient] = useState<Client | null>(null);
    const [isConnected, setIsConnected] = useState(false);
    const { jwt } = useAuth();

    const accessToken = jwt;
    useEffect(() => {

        if (!accessToken    ) return; // Don't connect if user is not logged in

    const socketUrl = `ws://localhost:8082/ws?access_token=${accessToken}`;
    const client = new Client({
      brokerURL: socketUrl,
      connectHeaders: {
        Authorization: "Bearer " + accessToken,
      },
                   reconnectDelay: 1000, // Auto-reconnect after 5 seconds
            onConnect: () => {
                console.log("Connected to WebSocket --");
                setIsConnected(true);
            },
            onDisconnect: () => {
                console.log("Disconnected from WebSocket");
                setIsConnected(false);
            },
            onStompError: (frame) => {
                console.error("STOMP Error: ", frame);
            },
        });

        client.activate();
        setStompClient(client);

        return () => {
            client.deactivate(); // Clean up on unmount
        };
    },[accessToken])
    return (
        <SocketContext.Provider value={{ stompClient, isConnected }}>
            {children}
        </SocketContext.Provider>
    );
};

export const useWebSocket = (): SocketContextType => {
    const context = useContext(SocketContext);
    if (!context) {
      throw new Error('useWebSocket must be used within a WebSocketProvider');
    }
    return context;
  };
