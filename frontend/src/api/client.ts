import createClient from "openapi-fetch";
import { API_BASE_URL } from "../config/api";
import type { components, paths } from "./schema";
import { session } from "./session";

export type Schemas = components["schemas"];

/** A failed request. `response.data` is the server's problem body. */
export class ApiError extends Error {
  readonly response: { status: number; data: { message?: string; fieldErrors?: Record<string, string> } };

  constructor(status: number, body: unknown) {
    const data = (body && typeof body === "object" ? body : {}) as ApiError["response"]["data"];
    super(data.message ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.response = { status, data };
  }
}

/** Adds the access token, and on a 401 refreshes once and repeats the request. */
const authorizedFetch = async (request: Request): Promise<Response> => {
  // Sign-in and registration set the refresh cookie and need no token.
  if (new URL(request.url).pathname.startsWith("/api/auth/")) {
    return fetch(request);
  }
  const send = (token: string | null) => {
    const attempt = request.clone();
    if (token) attempt.headers.set("Authorization", `Bearer ${token}`);
    return fetch(attempt);
  };
  const response = await send(await session.freshToken());
  if (response.status !== 401 || !(await session.refresh())) {
    return response;
  }
  return send(session.token());
};

/** Typed against the backend's OpenAPI contract: a route or payload mismatch fails the build. */
export const api = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: "include",
  fetch: authorizedFetch,
});

/** Resolves with the response body, or throws an ApiError. */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call;
  if (!response.ok) {
    throw new ApiError(response.status, error);
  }
  return data as T;
}
