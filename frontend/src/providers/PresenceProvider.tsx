import React, { createContext, useContext,useState, useEffect, useRef } from "react";
import api from "../services/api";
import { useAuth } from "./AuthProvider";
import { useWebSocketSender } from "./WebSocketProvider";
import { UserStatus } from "../types/status";
import { useWebSocketTopic } from "./WebSocketProvider";

interface PresenceContextType {
  status: UserStatus;
  sendHeartbeat: () => void;
  sendActivity: () => void;

  updateCustomStatus: (s: UserStatus) => Promise<void>;
  clearCustomStatus: () => Promise<void>;
}

const PresenceContext = createContext<PresenceContextType | null>(null);

export const PresenceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { messages } = useWebSocketTopic("/topic/status");

const [status,setStatus]= useState(UserStatus.ONLINE);
const { isLoggedIn, id } = useAuth();
  const { sendMessage, connected } = useWebSocketSender();
useEffect(() => {
  if (!isLoggedIn) return;

  (async () => {
    try {
      const { data } = await api.get("/users/me/status");

      setStatus(
        data.status);

    } catch (err) {
      console.error("Failed to fetch self status", err);
    }
  })();
}, [isLoggedIn]);


 useEffect(() => {
  if (messages.length === 0 || !id) return;

  const last = messages[messages.length - 1];
console.log("user id from status"+last.userId+ " current user id"+ id );
  // only self updates
  if (last.userId != id) return;

 
      setStatus(
        last.status); 
  console.log("set new status "+last.status);
}, [messages, id]);

  useEffect(() => {
    console.log("messages", messages);
  }, [messages]);
  // =========================
  // HEARTBEAT LOOP
  // =========================
  useEffect(() => {


    if (!connected || !isLoggedIn) return;

    const interval = setInterval(() => {
      sendMessage({}, "/app/heartbeat");
    }, 10000);

    return () => clearInterval(interval);
  }, [connected, isLoggedIn]);

  // =========================
  // METHODS
  // =========================
  const sendHeartbeat = () => {
    sendMessage({}, "/app/heartbeat");
  };

  const sendActivity = () => {
    sendMessage({}, "/app/activity");
  };

  const updateCustomStatus = async (status: string) => {
    await api.post(`/users/status/custom?status=${status}`);
  };

  const clearCustomStatus = async () => {
    await api.delete(`/users/status/custom`);
  };

  return (
    <PresenceContext.Provider
      value={{status,sendHeartbeat, sendActivity, updateCustomStatus, clearCustomStatus }}
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