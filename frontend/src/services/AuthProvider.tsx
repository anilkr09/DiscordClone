import React, { createContext, useContext, useEffect, useState, useCallback, ReactNode } from "react";
import AuthService from "../services/auth.service"; // Import your authentication service

// Define the authentication context type
interface AuthContextType {
  isLoggedIn: boolean;
  jwt: string | null;
  id: string | null;
  username: string | null;
  login: (email: string, password: string) => Promise<void>;
  signup: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

// Create the context
const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps): JSX.Element => {
  const [jwt, setJwtState] = useState<string | null>(() => localStorage.getItem("accessToken"));
  const [username, setUsernameState] = useState<string | null>(() => localStorage.getItem("username"));
    const [id, setId] = useState<string | null>(() => localStorage.getItem("id"));
  // Function to update JWT and sync it with localStorage
  const setJwt = useCallback((token: string | null) => {
    setJwtState(token);
    if (token) {
      localStorage.setItem("accessToken", token);
    } else {
      localStorage.removeItem("accessToken");
    }
  }, []);

  // Function to update Username and sync it with localStorage
  const setUsername = useCallback((name: string | null) => {
    setUsernameState(name);
    if (name) {
      localStorage.setItem("username", name);
    } else {
      localStorage.removeItem("username");
    }
  }, []);

  // Login function
  const login = useCallback(async (email: string, password: string) => {
    try {
      const response = await AuthService.login({username: email, password: password});
      setJwt(response.accessToken);
      setUsername(response.username);
      setId(response.userId.toString());
      localStorage.setItem("refreshToken", response.refreshToken);
    } catch (error) {
      console.error("Login failed:", error);
      throw error;
    }
  }, [setJwt, setUsername]);

  // Signup function
  const signup = useCallback(async (username: string, email: string, password: string) => {
    try {
      const response = await AuthService.register({ username: username, email: email, password: password});
      setJwt(response.accessToken);
      setUsername(response.username);
      localStorage.setItem("refreshToken", response.refreshToken);
    } catch (error) {
      console.error("Signup failed:", error);
      throw error;
    }
  }, [setJwt, setUsername]);

  // Logout function
  const logout = useCallback(() => {
    setJwt(null);
    setUsername(null);
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("username");
  }, []);

  useEffect(() => {
    setJwtState(localStorage.getItem("accessToken"));
    setUsernameState(localStorage.getItem("username"));
    
    const handleStorageChange = () => {
      setJwtState(localStorage.getItem("accessToken"));
      setUsernameState(localStorage.getItem("username"));
    };
    
    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, []);

  const authContextValue: AuthContextType = {
    isLoggedIn: !!jwt,
    jwt,
    id,
    username,
    login,
    signup,
    logout
  };

  return (
    <AuthContext.Provider value={authContextValue}>
      {children}
    </AuthContext.Provider>
  );
};

// Custom hook to use authentication context
export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};

export default AuthProvider;
