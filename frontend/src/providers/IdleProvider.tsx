import React, {
  useEffect,
  useRef,
  useCallback,
} from "react";

import { debounce } from "lodash";

import { usePresence } from "./PresenceProvider";
import { UserStatus } from "../types/status";

export const IdleProvider: React.FC<{
  children: React.ReactNode;
}> = ({ children }) => {

  const { sendActivity, status } =
    usePresence();

  const idleTimer =
    useRef<ReturnType<
      typeof setTimeout
    > | null>(null);

  // =========================================
  // MARK USER AS IDLE
  // =========================================
  const markIdle = useCallback(() => {

    console.log(
      "###idle - user became idle"
    );

  }, []);

  // =========================================
  // HANDLE USER ACTIVITY
  // =========================================
  const handleActivity = useCallback(() => {

    console.log(
      "###idle - activity detected | status:",
      status
    );

    // only notify server if user
    // is not already ONLINE
    if (
      status !== UserStatus.ONLINE
    ) {

      console.log(
        "###idle - sending activity event"
      );

      sendActivity();

      console.log(
        "###idle - user marked active"
      );
    }

    // reset idle timer
    if (idleTimer.current) {

      console.log(
        "###idle - clearing old idle timer"
      );

      clearTimeout(idleTimer.current);
    }

    console.log(
      "###idle - starting new idle timer"
    );

    idleTimer.current = setTimeout(
      markIdle,
      30000
    );

  }, [
    markIdle,
    sendActivity,
    status,
  ]);

  // =========================================
  // REGISTER EVENT LISTENERS
  // =========================================
  useEffect(() => {

    console.log(
      "###idle - provider mounted"
    );

    const handler = debounce(handleActivity, 1000, { leading: true })

    const events = [
      "mousemove",
      "keydown",
      "click",
      "touchstart",
    ];

    console.log(
      "###idle - registering events",
      events
    );

    events.forEach((event) => {

      window.addEventListener(
        event,
        handler
      );
    });

    return () => {

      console.log(
        "###idle - provider cleanup"
      );

      handler.cancel();

      events.forEach((event) => {

        window.removeEventListener(
          event,
          handler
        );
      });

      if (idleTimer.current) {

        console.log(
          "###idle - clearing idle timer"
        );

        clearTimeout(
          idleTimer.current
        );
      }
    };

  }, [handleActivity]);

  return <>{children}</>;
};