import type { ApiErrorBody, Session } from "../types";

const configuredBaseUrl =
  window.__PROCEL_CONFIG__?.API_BASE_URL ?? "http://localhost:8080";

export const apiBaseUrl = configuredBaseUrl.replace(/\/+$/, "");

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  session?: Session | null,
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (session?.accessToken) {
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let body: ApiErrorBody = {};
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      // The API may return an empty body for infrastructure errors.
    }
    throw new ApiError(
      body.message ?? `Falha na requisicao (${response.status})`,
      response.status,
      body.error,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
