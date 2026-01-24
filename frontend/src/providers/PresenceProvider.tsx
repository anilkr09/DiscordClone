import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { UserStatus } from "../types/status";
import { useWebSocketTopic } from "./WebSocketProvider.tsx";
import api from "../services/api.ts";

import { useAuth } from "./AuthProvider.tsx";

interface CustomStatus {
  status: UserStatus;
  expiresAt: Date;
}

interface PresenceContextType {
  status: UserStatus;
  setStatus: (s: UserStatus) => void;
  customStatus: CustomStatus;
  updateCustomStatus: (s: UserStatus) => void;
  isInitialized: boolean;
}

const PresenceContext = createContext<PresenceContextType | null>(null);

export const PresenceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [status, setStatus] = useState(UserStatus.ONLINE);
  const [customStatus, setCustomStatus] = useState<CustomStatus>({
    status: UserStatus.ONLINE,
    expiresAt: new Date(Date.now() + 86400000),
  });
  const [isInitialized, setIsInitialized] = useState(false);

  const statusRef = useRef(status);
  const customStatusRef = useRef(customStatus);

  const { id, isLoggedIn } = useAuth();
  const { sendMessage, connected } = useWebSocketTopic("/app/status");

  // init from server
  useEffect(() => {
    if (!isLoggedIn || !connected || isInitialized) return;

    (async () => {
      try {
        const { data } = await api.get(`/users/${id}/status`);
        const expired = new Date() > new Date(data.expiresAt);

        setCustomStatus(
          expired
            ? { status: UserStatus.ONLINE, expiresAt: new Date(Date.now() + 86400000) }
            : { status: data.customStatus, expiresAt: new Date(data.expiresAt) }
        );
      } finally {
        setIsInitialized(true);
      }
    })();
  }, [isLoggedIn, connected, isInitialized]);

  // sync status to backend
  useEffect(() => {
    if (!isInitialized || !connected) return;
    statusRef.current = status;
    sendMessage({ currentStatus: status });
  }, [status, connected, isInitialized]);

  // keep status aligned with custom status
  useEffect(() => {
    if (!isInitialized) return;
    customStatusRef.current = customStatus;
    if (customStatus.status !== status) {
      setStatus(customStatus.status);
    }
  }, [customStatus]);

  const updateCustomStatus = (newStatus: UserStatus) => {
    console.log("Updating custom status to:", newStatus);
    const payload = {
      status: newStatus,
      expiresAt: new Date(Date.now() + 86400000),
    };
    setCustomStatus(payload);
    console.log("connected", connected);
    if (connected) sendMessage({ customStatus: newStatus });
  };

  return (
    <PresenceContext.Provider
      value={{ status, setStatus, customStatus, updateCustomStatus, isInitialized }}
    >
      {children}
    </PresenceContext.Provider>
  );
};

export const usePresence = () => {
  const ctx = useContext(PresenceContext);
  if (!ctx) throw new Error("usePresence must be used within PresenceProvider");
  return ctx;
};
