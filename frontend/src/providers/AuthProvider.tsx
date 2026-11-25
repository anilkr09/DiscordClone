import { createContext, useContext, useEffect, useState, useCallback, useRef, ReactNode } from "react";
import AuthService from "../services/auth.service";
import { session, SessionUser } from "../api/session";
import { queryClient } from "../lib/reactQuery";
import { forgetUnconfirmed } from "../features/messages/cache";

interface AuthContextType {
  isLoggedIn: boolean;
  id: string | null;
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  signup: (username: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** False until the first attempt to restore a session has finished. */
  authInitialized: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/** Drops everything cached for whoever was signed in. */
const forgetUserData = () => {
  queryClient.clear();
  forgetUnconfirmed();
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [authInitialized, setAuthInitialized] = useState(false);
  const previousId = useRef<number | null>(null);

  useEffect(() => {
    const unsubscribe = session.subscribe((next) => {
      // Covers sign-out, an ended session, and a different account taking over.
      if (previousId.current !== null && previousId.current !== (next?.id ?? null)) {
        forgetUserData();
      }
      previousId.current = next?.id ?? null;
      setUser(next);
    });

    // The refresh cookie, if still valid, restores the session after a reload.
    session.refresh().finally(() => setAuthInitialized(true));

    // Another tab signed out or switched account.
    const onStorage = (event: StorageEvent) => {
      if (event.key !== "currentUser") return;
      const other = session.user();
      if (!other) session.clear();
      else if (previousId.current !== null && other.id !== previousId.current) window.location.reload();
    };
    window.addEventListener("storage", onStorage);

    return () => {
      unsubscribe();
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  const login = useCallback(
    (username: string, password: string) => AuthService.login({ username, password }),
    [],
  );

  const signup = useCallback(
    (username: string, email: string, password: string) =>
      AuthService.register({ username, email, password }),
    [],
  );

  const logout = useCallback(() => session.logout(), []);

  return (
    <AuthContext.Provider
      value={{
        isLoggedIn: user !== null,
        id: user ? String(user.id) : null,
        username: user?.username ?? null,
        login,
        signup,
        logout,
        authInitialized,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
