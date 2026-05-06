import React, { useEffect, useRef } from "react";
import { debounce } from "lodash";
import { usePresence } from "./PresenceProvider";

export const IdleProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {

  const { sendActivity } = usePresence();
  const idleTimer = useRef<NodeJS.Timeout | null>(null);
  const isIdle = useRef(false);

  const markIdle = () => {
    isIdle.current = true;
  };

  const handleActivity = () => {

    if (isIdle.current) {
      sendActivity(); // 🔥 transition idle → active
      isIdle.current = false;
    }

    if (idleTimer.current) clearTimeout(idleTimer.current);

    idleTimer.current = setTimeout(markIdle, 30000);
  };

  useEffect(() => {

    const handler = debounce(handleActivity, 5000);

    ["mousemove", "keydown", "click"].forEach(event =>
      window.addEventListener(event, handler)
    );

    handleActivity(); // initial

    return () => {
      handler.cancel();
      ["mousemove", "keydown", "click"].forEach(event =>
        window.removeEventListener(event, handler)
      );
      if (idleTimer.current) clearTimeout(idleTimer.current);
    };
  }, []);

  return <>{children}</>;
};