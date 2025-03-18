import { useState, useEffect, useRef } from "react";

// Enum for user status
export enum UserStatus {
  ONLINE = "ONLINE",
  OFFLINE = "OFFLINE",
  IDLE = "IDLE",
  DO_NOT_DISTURB = "DO_NOT_DISTURB",
}

interface UserStatusHook {
  status: UserStatus;
  customStatus: string;
  setCustomStatus: (status: string) => void;
  setStatus: (status: UserStatus) => void;
}

export const useUserStatus = (idleThreshold: number = 10000): UserStatusHook => {
  const [status, setStatus] = useState<UserStatus>(UserStatus.ONLINE);
  const [customStatus, setCustomStatus] = useState<string>("");
  const lastActivityTime = useRef<number>(Date.now());
  const idleTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const resetIdleTimer = (): void => {
      lastActivityTime.current = Date.now();

      if (status === UserStatus.IDLE) {
        setStatus(UserStatus.ONLINE);
      }

      if (idleTimeout.current) {
        clearTimeout(idleTimeout.current);
      }

      idleTimeout.current = setTimeout(() => {
        setStatus(UserStatus.IDLE);
      }, idleThreshold);
    };

    const handleOnlineStatus = (): void => {
      setStatus(UserStatus.ONLINE);
      resetIdleTimer();
    };

    const handleOfflineStatus = (): void => {
      setStatus(UserStatus.OFFLINE);
    };

    const events: string[] = ["mousedown", "mousemove", "keypress", "scroll", "touchstart"];

    events.forEach((event) => {
      window.addEventListener(event, resetIdleTimer);
    });

    window.addEventListener("online", handleOnlineStatus);
    window.addEventListener("offline", handleOfflineStatus);

    resetIdleTimer();

    return () => {
      events.forEach((event) => {
        window.removeEventListener(event, resetIdleTimer);
      });
      window.removeEventListener("online", handleOnlineStatus);
      window.removeEventListener("offline", handleOfflineStatus);
      if (idleTimeout.current) {
        clearTimeout(idleTimeout.current);
      }
    };
  }, [idleThreshold, status]);

  return {
    status,
    customStatus,
    setCustomStatus,
    setStatus,
  };
};

export default useUserStatus;
