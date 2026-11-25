import createClient, { type Middleware } from "openapi-fetch";
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

const authentication: Middleware = {
  onRequest({ request, schemaPath }) {
    const token = session.token();
    if (token && !schemaPath.startsWith("/api/auth/")) {
      request.headers.set("Authorization", `Bearer ${token}`);
    }
    return request;
  },
  onResponse({ response, schemaPath }) {
    if (response.status === 401 && !schemaPath.startsWith("/api/auth/")) {
      session.expire();
    }
    return response;
  },
};

/** Typed against the backend's OpenAPI contract: a route or payload mismatch fails the build. */
export const api = createClient<paths>({ baseUrl: API_BASE_URL });
api.use(authentication);

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
