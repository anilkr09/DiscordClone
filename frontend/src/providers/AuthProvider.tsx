import React, { createContext, useContext, useEffect, useState, useCallback, ReactNode } from "react";
import AuthService from "../services/auth.service";

// Define the authentication context type
interface AuthContextType {
  isLoggedIn: boolean;
  jwt: string | null;
  id: string | null;
  username: string | null;
  login: (email: string, password: string) => Promise<void>;
  signup: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  authInitialized: boolean; // New flag to track initialization
}

// Create the context
const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

// TypeScript interface for decoded JWT token
interface DecodedToken {
  exp: number;
  [key: string]: any;
}

export const AuthProvider = ({ children }: AuthProviderProps): JSX.Element => {
  // Initialize states from localStorage
  const [jwt, setJwtState] = useState<string | null>(null);
  const [username, setUsernameState] = useState<string | null>(null);
  const [id, setIdState] = useState<string | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);
  const [authInitialized, setAuthInitialized] = useState<boolean>(false);

  // Function to decode JWT properly with TypeScript
  const decodeJWT = useCallback((token: string): DecodedToken => {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      console.error("Error decoding JWT:", error);
      return { exp: 0 };
    }
  }, []);

  // Initialize authentication state from localStorage
  useEffect(() => {
    console.log("🔄 Auth Provider initializing...");
    
    const initializeAuth = () => {
      const storedToken = localStorage.getItem("accessToken");
      console.log("📋 Found token in localStorage:", !!storedToken);
      
      if (storedToken) {
        try {
          // Validate token
          const decodedToken = decodeJWT(storedToken);
          const currentTime = Date.now() / 1000;
          
          if (decodedToken.exp > currentTime) {
            console.log("✅ Token is valid, expires:", new Date(decodedToken.exp * 1000).toLocaleString());
            
            // Set all auth state at once
            setJwtState(storedToken);
            setUsernameState(localStorage.getItem("username"));
            setIdState(localStorage.getItem("id"));
            setIsLoggedIn(true);
          } else {
            console.log("⚠️ Token expired, clearing auth state");
            clearAuthState();
          }
        } catch (error) {
          console.error("🛑 Error validating token on init:", error);
          clearAuthState();
        }
      } else {
        console.log("📭 No token found, starting unauthenticated");
        clearAuthState();
      }
      
      // Mark initialization as complete, regardless of outcome
      setAuthInitialized(true);
    };
    
    const clearAuthState = () => {
      // Clear localStorage
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("username");
      localStorage.removeItem("id");
      
      // Reset state
      setJwtState(null);
      setUsernameState(null);
      setIdState(null);
      setIsLoggedIn(false);
    };
    
    // Initialize auth state
    initializeAuth();
    
    // Listen for storage events (from other tabs)
    const handleStorageChange = (event: StorageEvent) => {
      if (event.key === "accessToken") {
        console.log("📢 Token changed in another tab, reinitializing auth");
        initializeAuth();
      }
    };
    
    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, [decodeJWT]);

  // Login function
  const login = useCallback(async (email: string, password: string) => {
    try {
      console.log("🔑 Login attempt for:", email);
      const response = await AuthService.login({username: email, password: password});
      console.log("✅ Login successful");
      
      if (!response || !response.accessToken) {
        throw new Error("Invalid response from auth service");
      }
      
      // Store auth data in localStorage first
      localStorage.setItem("accessToken", response.accessToken);
      localStorage.setItem("username", response.username);
      localStorage.setItem("id", response.userId.toString());
      
      if (response.refreshToken) {
        localStorage.setItem("refreshToken", response.refreshToken);
      }
      
      // Then update state
      setJwtState(response.accessToken);
      setUsernameState(response.username);
      setIdState(response.userId.toString());
      setIsLoggedIn(true);
      
      console.log("🔄 Auth state updated after login");
    } catch (error) {
      console.error("🛑 Login failed:", error);
      throw error;
    }
  }, []);

  // Signup function
  const signup = useCallback(async (username: string, email: string, password: string) => {
    try {
      console.log("📝 Signup attempt");
      console.log("username:", username, "email:", email,"password:", password);
      const response = await AuthService.register({ username, email, password });
      console.log("response value", response);
      if (!response || !response.accessToken) {
        throw new Error("Invalid response from auth service");
      }
      
      // Store auth data in localStorage
      localStorage.setItem("accessToken", response.accessToken);
      localStorage.setItem("username", response.username);
      
      if (response.userId) {
        localStorage.setItem("id", response.userId.toString());
      }
      
      if (response.refreshToken) {
        localStorage.setItem("refreshToken", response.refreshToken);
      }
      
      // Update state
      setJwtState(response.accessToken);
      setUsernameState(response.username);
      if (response.userId) {
        setIdState(response.userId.toString());
      }
      setIsLoggedIn(true);
      
      console.log("✅ Signup successful, auth state updated");
    } catch (error) {
      console.error("🛑 Signup failed:", error);
      throw error;
    }
  }, []);

  // Logout function
  const logout = useCallback(() => {
    console.log("🚪 Logout initiated");
    
    // Clear localStorage first
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("username");
    localStorage.removeItem("id");
    
    // Then update state
    setJwtState(null);
    setUsernameState(null);
    setIdState(null);
    setIsLoggedIn(false);
    
    console.log("✅ Logout complete");
  }, []);

  // Provide the auth context value
  const authContextValue: AuthContextType = {
    isLoggedIn,
    jwt,
    id,
    username,
    login,
    signup,
    logout,
    authInitialized // Expose initialization state
  };

  // Debug log when important values change
  useEffect(() => {
    console.log("🔐 Auth state updated:", { 
      isLoggedIn, 
      hasToken: !!jwt,
      authInitialized
    });
  }, [isLoggedIn, jwt, authInitialized]);

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