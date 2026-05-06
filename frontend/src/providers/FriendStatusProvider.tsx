import React, { createContext, useContext, useEffect, useState } from "react";
import { UserStatus } from "../types/status";
import { useWebSocketTopic } from "./WebSocketProvider";
import api from "../services/api";
import { useAuth } from "./AuthProvider";

interface FriendStatusMap {
  [userId: number]: UserStatus;
}

interface FriendStatusContextType {
  friendStatuses: FriendStatusMap;
  getStatus: (id: number) => UserStatus;
}

const FriendStatusContext = createContext<FriendStatusContextType | null>(null);

export const FriendStatusProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [friendStatuses, setFriendStatuses] = useState<FriendStatusMap>({});
  const { messages, connected } = useWebSocketTopic("/topic/status");
  const { isLoggedIn } = useAuth();

  // ✅ initial load (friends only)
  useEffect(() => {
    if (!isLoggedIn) return;

    (async () => {
      const { data } = await api.get("/users/friends/status");
      const map: FriendStatusMap = {};
      data.forEach((s: any) => (map[s.userId] = s.status));
      setFriendStatuses(map);
    })();
  }, [isLoggedIn]);

  // ✅ realtime updates
  useEffect(() => {
    if (!connected || messages.length === 0) return;

    const last = messages[messages.length - 1];

    setFriendStatuses(prev => ({
      ...prev,
      [last.userId]: last.status
    }));
  }, [messages, connected]);

  const getStatus = (id: number) => friendStatuses[id] ?? UserStatus.OFFLINE;

  return (
    <FriendStatusContext.Provider value={{ friendStatuses, getStatus }}>
      {children}
    </FriendStatusContext.Provider>
  );
};

export const useFriendStatus = () => {
  const ctx = useContext(FriendStatusContext);
  if (!ctx) throw new Error("useFriendStatus must be used within FriendStatusProvider");
  return ctx;
};