import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
} from "react";

import { api, unwrap } from "../api/client";

import { useAuth } from "./AuthProvider";

import {
  useWebSocketSender,
  useWebSocketTopic,
} from "./WebSocketProvider";

import { UserStatus } from "../types/status";

interface PresenceContextType {
  status: UserStatus;

  sendHeartbeat: () => void;

  sendActivity: () => void;

  updateCustomStatus: (
    s: UserStatus
  ) => Promise<void>;

  clearCustomStatus: () => Promise<void>;
}

const PresenceContext =
  createContext<PresenceContextType | null>(
    null
  );

export const PresenceProvider: React.FC<{
  children: React.ReactNode;
}> = ({ children }) => {

  const { messages } =
    useWebSocketTopic("/topic/status");

  const [status, setStatus] =
    useState<UserStatus>(
      UserStatus.ONLINE
    );

  const { isLoggedIn, id } = useAuth();

  const { sendMessage, connected } =
    useWebSocketSender();

  // =========================================
  // FETCH SELF STATUS
  // =========================================
  useEffect(() => {

    if (!isLoggedIn) return;

    const fetchStatus = async () => {

      try {

        console.log(
          "###status - fetching self status"
        );

        const data = await unwrap(api.GET("/api/users/me/status"));

        console.log(
          "###status - fetched self status",
          data
        );

        setStatus(data.status as UserStatus);

      } catch (err) {

        console.error(
          "###status - failed to fetch self status",
          err
        );
      }
    };

    fetchStatus();

  }, [isLoggedIn]);

  // =========================================
  // WEBSOCKET STATUS UPDATES
  // =========================================
  useEffect(() => {

    if (
      messages.length === 0 ||
      !id
    ) {
      return;
    }

    const last =
      messages[messages.length - 1];

    console.log(
      "###status - websocket message",
      last
    );

    console.log(
      "###status - websocket userId:",
      last.userId,
      "current userId:",
      id
    );

    if (
      String(last.userId) !==
      String(id)
    ) {

      console.log(
        "###status - ignoring other user's status"
      );

      return;
    }

    console.log(
      "###status - applying new status:",
      last.status
    );

    setStatus(last.status);

  }, [messages, id]);

  // =========================================
  // DEBUG WEBSOCKET MESSAGES
  // =========================================
  useEffect(() => {

    console.log(
      "###status - all websocket messages",
      messages
    );

  }, [messages]);

  // =========================================
  // HEARTBEAT LOOP
  // =========================================
  useEffect(() => {

    if (
      !connected ||
      !isLoggedIn
    ) {
      return;
    }

    console.log(
      "###status - heartbeat loop started"
    );

    const interval =
      setInterval(() => {

        sendMessage(
          {},
          "/app/heartbeat"
        );

        console.log(
          "###status - heartbeat auto sent"
        );

      }, 10000);

    return () => {

      console.log(
        "###status - heartbeat loop stopped"
      );

      clearInterval(interval);
    };

  }, [
    connected,
    isLoggedIn,
    sendMessage,
  ]);

  // =========================================
  // MEMOIZED METHODS
  // =========================================

  const sendHeartbeat =
    useCallback(() => {

      console.log(
        "###status - manual heartbeat sending"
      );

      sendMessage(
        {},
        "/app/heartbeat"
      );

      console.log(
        "###status - manual heartbeat sent"
      );

    }, [sendMessage]);

  const sendActivity =
    useCallback(() => {

      console.log(
        "###status - sending activity"
      );

      sendMessage(
        {},
        "/app/activity"
      );

      console.log(
        "###status - activity sent"
      );

    }, [sendMessage]);

  const updateCustomStatus =
    useCallback(
      async (
        status: UserStatus
      ) => {

        try {

          console.log(
            "###status - updating custom status:",
            status
          );

          await unwrap(
            api.POST("/api/users/status/custom", { params: { query: { status } } }),
          );

          console.log(
            "###status - custom status updated");

        } catch (error) {

          console.error(
            "###status - failed to update custom status",
            error
          );
        }
      },
      []
    );

  const clearCustomStatus =
    useCallback(async () => {

      try {

        console.log(
          "###status - clearing custom status"
        );

        await unwrap(api.DELETE("/api/users/status/custom"));

        console.log(
          "###status - custom status cleared");

      } catch (error) {

        console.error(
          "###status - failed to clear custom status",
          error
        );
      }

    }, []);

  // =========================================
  // MEMOIZED CONTEXT VALUE
  // =========================================
  const value = useMemo(
    () => ({
      status,
      sendHeartbeat,
      sendActivity,
      updateCustomStatus,
      clearCustomStatus,
    }),
    [
      status,
      sendHeartbeat,
      sendActivity,
      updateCustomStatus,
      clearCustomStatus,
    ]
  );

  return (
    <PresenceContext.Provider
      value={value}
    >
      {children}
    </PresenceContext.Provider>
  );
};

export const usePresence = () => {

  const ctx =
    useContext(PresenceContext);

  if (!ctx) {

    throw new Error(
      "usePresence must be used within PresenceProvider"
    );
  }

  return ctx;
};