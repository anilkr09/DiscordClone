import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from "react";
import { Client, StompSubscription, IMessage } from "@stomp/stompjs";
import { useAuth } from "./AuthProvider.tsx";

// WebSocket Context Type
interface WebSocketContextType {
    connected: boolean;
    subscribeToTopic: (topic: string) => StompSubscription | null;
    unsubscribeFromTopic: (topic: string) => void;
    sendMessage: (destination: string, message: any) => void;
    messageStore: Record<string, any[]>;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

// Define props for the provider
interface WebSocketProviderProps {
    children: ReactNode;
}

// WebSocket Provider Component
export const WebSocketProvider: React.FC<WebSocketProviderProps> = ({ children }) => {
    const [client, setClient] = useState<Client | null>(null);
    const [connected, setConnected] = useState(false);
    const [subscriptions, setSubscriptions] = useState<Record<string, StompSubscription>>({});
    const [messageStore, setMessageStore] = useState<Record<string, any[]>>({});

    // const { jwt } = useAuth();

    const accessToken = localStorage.getItem("accessToken");

    useEffect(() => {
        if ( connected) return; // Prevent double connection

        if (!accessToken) return; // Don't connect if user is not logged in
        console.log("accessToken", accessToken);
        console.log("Initializing WebSocket connection...");
        const socketUrl = `ws://localhost:8082/ws`;

        const stompClient = new Client({
            brokerURL: socketUrl,
            connectHeaders: {
                Authorization: `Bearer ${accessToken}`,
            },
            onConnect: () => {
                console.log("STOMP client connected");
                setConnected(true);
                setClient(stompClient);
            },
            onDisconnect: () => {
                console.log("STOMP client disconnected");
                setConnected(false);
                setClient(null);
            },
            // onStompError: (frame) => {
            //     console.error("Broker error:", frame.headers["message"]);
            // },
            onStompError: (frame) => {
                console.error('Broker error:', frame.headers.message);
                // Handle the error
            },
            onWebSocketError: (event) => {
                console.error('WebSocket error:', event);
            }
            // ,

            // debug: (str) => {
            //     console.log('STOMP Debug:', str);
            // }
        
        });

        stompClient.activate();

        return () => {
            console.log("Cleaning up WebSocket connection...");
            client?.deactivate();
            stompClient.deactivate();
            setConnected(false);
            setClient(null);
        };
    }, [accessToken]);

    // Subscribe to a topic
    const subscribeToTopic = useCallback(
        (topic: string): StompSubscription | null => {
            if (!client || !connected) return null;

            const subscription = client.subscribe(topic, (message: IMessage) => {
                try {
                    const payload = JSON.parse(message.body);
                    setMessageStore((prev) => ({
                        ...prev,
                        [topic]: [...(prev[topic] || []), payload],
                    }));
                } catch (e) {
                    console.error("Error parsing message:", e);
                }
            });

            setSubscriptions((prev) => ({
                ...prev,
                [topic]: subscription,
            }));

            return subscription;
        },
        [client, connected]
    );

    // Unsubscribe from a topic
    const unsubscribeFromTopic = useCallback(
        (topic: string): void => {
            setSubscriptions((prev) => {
                if (prev[topic]) {
                    prev[topic].unsubscribe();
                    const newSubs = { ...prev };
                    delete newSubs[topic];
                    return newSubs;
                }
                return prev;
            });
        },
        []
    );

    // Send a message to a WebSocket destination
    const sendMessage = useCallback(
        (destination: string, message: any): void => {
            if (!client || !connected) {
                console.warn("Cannot send message: WebSocket is not connected.");
                return;
            }

            client.publish({
                destination,
                body: JSON.stringify(message),
            });

            console.log(`Message sent -- ${destination}:`, message);
        },
        [client, connected]
    );

    return (
        <WebSocketContext.Provider value={{ connected, subscribeToTopic, unsubscribeFromTopic, sendMessage, messageStore }}>
            {children}
        </WebSocketContext.Provider>
    );
};

// Custom Hook for WebSocket Context
export const useWebSocket = () => {
    const context = useContext(WebSocketContext);
    if (!context) {
        throw new Error("useWebSocket must be used within a WebSocketProvider");
    }
    return context;
};

// Custom Hook to Subscribe to a WebSocket Topic (✅ Now includes `sendMessage`)
export const useWebSocketTopic = (topic: string) => {
    const { connected, subscribeToTopic, unsubscribeFromTopic, sendMessage, messageStore } = useWebSocket();

    useEffect(() => {
        if (connected) {
            console.log(`Subscribing to topic: ${topic}`);
            const subscription = subscribeToTopic(topic);
            return () => {
                console.log(`Unsubscribing from topic: ${topic}`);
                if (subscription) {
                    subscription.unsubscribe();
                }
            };
        }
    }, [topic, connected, subscribeToTopic, unsubscribeFromTopic]);

    const sendMessageToTopic = useCallback(
        (message: any) => {
            sendMessage(topic, message);
        },
        [sendMessage, topic]
    );

    return { connected, messages: messageStore[topic] || [], sendMessage: sendMessageToTopic };
};