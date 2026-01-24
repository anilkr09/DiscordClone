import React, { useEffect, useRef } from "react";
import { debounce } from "lodash";
import { UserStatus } from "../types/status";
import { usePresence } from "./PresenceProvider";

export const IdleProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { status, setStatus, customStatus } = usePresence();
  const idleTimer = useRef<NodeJS.Timeout | null>(null);

  const startIdleTimer = () => {
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = setTimeout(() => {
      if (status === UserStatus.ONLINE && customStatus.status === UserStatus.ONLINE) {
        setStatus(UserStatus.IDLE);
      }
    }, 60_000);
  };

  const resetIdleTimer = () => {
    if (status !== UserStatus.ONLINE && customStatus.status === UserStatus.ONLINE) {
      setStatus(UserStatus.ONLINE);
    }
    startIdleTimer();
  };

  useEffect(() => {
    const handler = debounce(resetIdleTimer, 3000);

    window.addEventListener("mousemove", handler);
    window.addEventListener("keydown", handler);
    window.addEventListener("click", handler);

    startIdleTimer();

    return () => {
      handler.cancel();
      window.removeEventListener("mousemove", handler);
      window.removeEventListener("keydown", handler);
      window.removeEventListener("click", handler);
      if (idleTimer.current) clearTimeout(idleTimer.current);
    };
  }, [status, customStatus.status]);

  return <>{children}</>;
};
