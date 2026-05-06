import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
  useRef, // ✅ ADDED: used to prevent duplicate connections
} from "react";
import { Client, StompSubscription, IMessage } from "@stomp/stompjs";
import { useAuth } from "./AuthProvider.tsx";
import { registerMessageSocket } from "../websocket/message.socket.ts";
import { queryClient } from "../lib/reactQuery.ts";
import { handleFriendEvent } from "../websocket/friends.events.ts";
import { WS_BASE_URL } from "../config/api";

// WebSocket Context Type
interface WebSocketContextType {
  connected: boolean;
  client: Client | null;
  subscribeToTopic: (topic: string) => StompSubscription | null;
  unsubscribeFromTopic: (topic: string) => void;
  sendMessage: (destination: string, message: any) => void;
  messageStore: Record<string, any[]>;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(
  undefined
);

interface WebSocketProviderProps {
  children: ReactNode;
}

export const WebSocketProvider: React.FC<WebSocketProviderProps> = ({
  children,
}) => {
  const [client, setClient] = useState<Client | null>(null);
  const [connected, setConnected] = useState(false);
  const [subscriptions, setSubscriptions] = useState<
    Record<string, StompSubscription>
  >({});
  const [messageStore, setMessageStore] = useState<Record<string, any[]>>({});

  const { isLoggedIn } = useAuth();
  const accessToken = localStorage.getItem("accessToken");

  // ✅ NEW: Prevent multiple WebSocket connections (fixes duplicate connections)
  const isConnecting = useRef(false);

  useEffect(() => {
    // ✅ FIX: Prevent duplicate connections (StrictMode / re-renders safe)
    if (isConnecting.current) return;
    if (!accessToken) return;

    isConnecting.current = true;

    console.log("Initializing WebSocket connection...");
    const socketUrl = `${WS_BASE_URL}/ws`;

    const stompClient = new Client({
      brokerURL: socketUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },

      onConnect: () => {
        console.log("STOMP client connected");

        setConnected(true);
        setClient(stompClient);

        // existing logic
        registerMessageSocket(stompClient);

        // ✅ NOTE: This subscription is safe because it runs once per connection
        stompClient.subscribe("/user/queue/friends", (message) => {
          const event = JSON.parse(message.body);
          handleFriendEvent(queryClient, event.type, event.payload);
        });
      },

      onDisconnect: () => {
        console.log("STOMP client disconnected");

        setConnected(false);
        setClient(null);

        // ✅ RESET connection guard on disconnect
        isConnecting.current = false;
      },

      onStompError: (frame) => {
        console.error("Broker error:", frame.headers.message);
      },

      onWebSocketError: (event) => {
        console.error("WebSocket error:", event);
      },
    });

    stompClient.activate();

    return () => {
      console.log("Cleaning up WebSocket connection...");

      // ✅ FIX: ensure proper cleanup
      stompClient.deactivate();

      setConnected(false);
      setClient(null);

      // ✅ RESET flag so reconnection is possible
      isConnecting.current = false;
    };
  }, [isLoggedIn, accessToken]);

  // ============================
  // ✅ FIXED SUBSCRIBE FUNCTION
  // ============================
  const subscribeToTopic = useCallback(
    (topic: string): StompSubscription | null => {
      if (!client || !connected) return null;

      // ✅ CRITICAL FIX:
      // Prevent duplicate subscriptions to same topic
      if (subscriptions[topic]) {
        return subscriptions[topic];
      }

      const subscription = client.subscribe(topic, (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body);

          // ✅ OPTIONAL SAFETY: deduplicate last message
          setMessageStore((prev) => {
            const prevMessages = prev[topic] || [];
            const last = prevMessages[prevMessages.length - 1];

            // prevent exact duplicate entries
            // if (
            //   last &&
            //   JSON.stringify(last) === JSON.stringify(payload)
            // ) {
            //   return prev;
            // }

            return {
              ...prev,
              [topic]: [...prevMessages, payload],
            };
          });
        } catch (e) {
          console.error("Error parsing message:", e);
        }
      });

      // store subscription
      setSubscriptions((prev) => ({
        ...prev,
        [topic]: subscription,
      }));

      return subscription;
    },
    [client, connected, subscriptions] // ✅ include subscriptions to track existing ones
  );

  // ============================
  // ✅ FIXED UNSUBSCRIBE
  // ============================
  const unsubscribeFromTopic = useCallback((topic: string): void => {
    setSubscriptions((prev) => {
      if (prev[topic]) {
        // ✅ properly unsubscribe existing subscription
        prev[topic].unsubscribe();

        const newSubs = { ...prev };
        delete newSubs[topic];

        return newSubs;
      }
      return prev;
    });
  }, []);

  // ============================
  // SEND MESSAGE (unchanged)
  // ============================
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
    <WebSocketContext.Provider
      value={{
        connected,
        client,
        subscribeToTopic,
        unsubscribeFromTopic,
        sendMessage,
        messageStore,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

// ============================
// HOOK
// ============================
export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
};

// ============================
// ✅ FIXED TOPIC HOOK
// ============================
export const useWebSocketTopic = (topic: string) => {
  const {
    connected,
    subscribeToTopic,
    unsubscribeFromTopic,
    sendMessage,
    messageStore,
  } = useWebSocket();

  useEffect(() => {
    if (!connected) return;

    console.log(`Subscribing to topic: ${topic}`);

    // subscribe
    subscribeToTopic(topic);

    return () => {
      console.log(`Unsubscribing from topic: ${topic}`);

      // ✅ FIX: always unsubscribe via provider (not local variable)
      unsubscribeFromTopic(topic);
    };
  }, [topic, connected]); // ✅ FIX: removed unstable deps

  const sendMessageToTopic = useCallback(
    (message: any) => {
      sendMessage(topic, message);
    },
    [sendMessage, topic]
  );

  return {
    connected,
    messages: messageStore[topic] || [],
    sendMessage: sendMessageToTopic,
  };
};

export const useWebSocketSender = () => {
  const { client, connected } = useWebSocket();

  const sendMessage = useCallback(
    ( payload,destination ) => {
      if (!client || !connected) {
        console.warn("WebSocket not connected");
        return;
      }

      client.publish({
        destination,
        body: JSON.stringify(payload ?? {}),
      });
    },
    [client, connected]
  );

  return { sendMessage, connected };
};