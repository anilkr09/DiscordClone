import createClient from "openapi-fetch";
import { jwtDecode } from "jwt-decode";
import { API_BASE_URL } from "../config/api";
import type { components, paths } from "./schema";

/**
 * The signed-in session. The access token lives only in memory. The refresh token is an HttpOnly
 * cookie the page cannot read; the browser sends it to /api/auth/refresh alone.
 */
export interface SessionUser {
  id: number;
  username: string;
}

type AuthResponse = components["schemas"]["JwtAuthResponse"];
type Listener = (user: SessionUser | null) => void;

const USER_KEY = "currentUser";
const REFRESH_LEEWAY_SECONDS = 30;
// Left behind by earlier versions, which kept tokens in storage.
for (const key of ["accessToken", "refreshToken", "username", "id"]) {
  localStorage.removeItem(key);
}

const auth = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: "include",
  // Required by the server on cookie-authenticated calls; cross-origin pages cannot add it.
  headers: { "X-CSRF-Protection": "1" },
});

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<Listener>();

const storedUser = (): SessionUser | null => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) ?? "null");
  } catch {
    return null;
  }
};

const notify = (user: SessionUser | null) => listeners.forEach((listener) => listener(user));

const isExpiring = (token: string): boolean => {
  const { exp } = jwtDecode(token);
  return exp === undefined || exp - REFRESH_LEEWAY_SECONDS <= Date.now() / 1000;
};

const requestRefresh = async (): Promise<boolean> => {
  try {
    const { data, response } = await auth.POST("/api/auth/refresh");
    if (response.ok && data) {
      session.start(data);
      return true;
    }
    if (response.status === 401) {
      session.clear();
    }
  } catch (error) {
    // The server is unreachable; keep the session and try again later.
    console.warn("Could not refresh the session", error);
  }
  return false;
};

export const session = {
  token: (): string | null => accessToken,
  user: storedUser,

  start(response: AuthResponse): void {
    accessToken = response.accessToken;
    const user = { id: response.userId, username: response.username };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    notify(user);
  },

  /** Gets a new access token. Concurrent callers, in this tab or another, share the work safely. */
  refresh(): Promise<boolean> {
    refreshing ??= (
      // A refresh token works once, so tabs must take turns; each then sends the newest cookie.
      navigator.locks ? navigator.locks.request("auth-refresh", requestRefresh) : requestRefresh()
    ).finally(() => {
      refreshing = null;
    });
    return refreshing;
  },

  /** The access token, renewed first if it is missing or about to expire. */
  async freshToken(): Promise<string | null> {
    if (!accessToken || isExpiring(accessToken)) {
      await session.refresh();
    }
    return accessToken;
  },

  async logout(): Promise<void> {
    try {
      await auth.DELETE("/api/auth/refresh");
    } catch (error) {
      console.warn("Could not reach the server to sign out", error);
    }
    session.clear();
  },

  /** Forgets the session in this tab. */
  clear(): void {
    accessToken = null;
    localStorage.removeItem(USER_KEY);
    notify(null);
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
