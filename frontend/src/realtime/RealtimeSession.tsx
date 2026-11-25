import { ReactNode, useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../providers/AuthProvider";
import { realtime, startRealtime, stopRealtime } from "./realtime";
import { useRealtimeConnected, useStompSubscription } from "./hooks";
import { applyEvent } from "./events";

const ACTIVITY_INTERVAL_MS = 15_000;
const ACTIVITY_EVENTS = ["mousemove", "keydown", "click", "touchstart", "scroll"] as const;

/** Reports that the page is connected, and that someone is using it. */
function usePresenceSignals(connected: boolean) {
  useEffect(() => {
    if (!connected) return;
    const ticker = new Worker(new URL("./ticker.worker.ts", import.meta.url), { type: "module" });
    ticker.onmessage = () => realtime.publish("/app/heartbeat");
    realtime.publish("/app/heartbeat");

    let lastSent = 0;
    // At most one report per interval, whatever the current status; the server works out the rest.
    const onActivity = () => {
      if (Date.now() - lastSent >= ACTIVITY_INTERVAL_MS && realtime.publish("/app/activity")) {
        lastSent = Date.now();
      }
    };
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, onActivity, { passive: true }));

    return () => {
      ticker.terminate();
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, onActivity));
    };
  }, [connected]);
}

/** Connects while someone is signed in, and listens for everything addressed to them. */
export function RealtimeSession({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useAuth();
  const queryClient = useQueryClient();
  const connected = useRealtimeConnected();
  const apply = (body: unknown) => applyEvent(queryClient, body);

  useEffect(() => {
    if (!isLoggedIn) return;
    startRealtime();
    return () => void stopRealtime();
  }, [isLoggedIn]);

  // Whatever happened while disconnected was missed, so fetch it again.
  useEffect(() => {
    if (!connected) return;
    for (const key of ["presence", "messages", "friends", "incomingRequests", "outgoingRequests", "channels"]) {
      void queryClient.invalidateQueries({ queryKey: [key] });
    }
  }, [connected, queryClient]);

  useStompSubscription(isLoggedIn ? "/user/queue/messages" : null, apply);
  useStompSubscription(isLoggedIn ? "/user/queue/friends" : null, apply);
  useStompSubscription(isLoggedIn ? "/user/queue/presence" : null, apply);
  useStompSubscription(isLoggedIn ? "/user/queue/errors" : null, apply);
  usePresenceSignals(connected);

  return <>{children}</>;
}
