import { useEffect, useRef, useSyncExternalStore } from "react";
import { realtime } from "./realtime";

/** Subscribes while mounted. Pass null to subscribe to nothing. */
export function useStompSubscription(destination: string | null, handler: (body: unknown) => void) {
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => {
    if (!destination) return;
    return realtime.subscribe(destination, (body) => latest.current(body));
  }, [destination]);
}

export const useRealtimeConnected = () => useSyncExternalStore(realtime.onChange, realtime.isConnected);
