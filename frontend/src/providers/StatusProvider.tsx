import React from "react";
import { PresenceProvider } from "./PresenceProvider";
import { IdleProvider } from "./IdleProvider";
import { FriendStatusProvider } from "./FriendStatusProvider";

export const StatusProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <PresenceProvider>
      <IdleProvider>
        <FriendStatusProvider>
          {children}
        </FriendStatusProvider>
      </IdleProvider>
    </PresenceProvider>
  );
};
