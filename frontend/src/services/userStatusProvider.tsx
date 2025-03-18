import React, { createContext, useContext, ReactNode } from "react";
import { useUserStatus } from "../hooks/useUserStatus"; // Adjust import path
import { UserStatus } from "../types/status"; // Adjust import path

// Define the context type
interface UserStatusContextType {
  status: UserStatus;
  customStatus: string;
  setCustomStatus: (status: string) => void;
  setStatus: (status: UserStatus) => void;
}

// Create the context
const UserStatusContext = createContext<UserStatusContextType | undefined>(undefined);

interface UserStatusProviderProps {
  children: ReactNode;
}

// Context Provider Component
export const UserStatusProvider = ({ children }: UserStatusProviderProps): JSX.Element => {
  const { status, customStatus, setCustomStatus, setStatus } = useUserStatus();

  return (
    <UserStatusContext.Provider value={{ status, customStatus, setCustomStatus, setStatus }}>
      {children}
    </UserStatusContext.Provider>
  );
};

// Custom Hook to Use User Status Context
export const useUserStatusContext = (): UserStatusContextType => {
  const context = useContext(UserStatusContext);
  if (!context) {
    throw new Error("useUserStatusContext must be used within a UserStatusProvider");
  }
  return context;
};

export default UserStatusProvider;
