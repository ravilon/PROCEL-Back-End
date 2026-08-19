import { ApiError } from "../lib/api";
import type { Session } from "../types/auth";
import type {
  RawTelemetryEvent,
  RawTelemetryEventPage,
  ReprocessTelemetryResponse,
  TelemetryEventFilters,
} from "../types/telemetry";

const configuredTelemetryBaseUrl =
  import.meta.env.VITE_TELEMETRY_API_URL
  ?? window.__PROCEL_CONFIG__?.TELEMETRY_API_URL
  ?? "http://localhost:8081";

export const telemetryApiBaseUrl = configuredTelemetryBaseUrl.replace(/\/+$/, "");

async function telemetryRequest<T>(
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

  const response = await fetch(`${telemetryApiBaseUrl}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let body: { message?: string; error?: string } = {};
    try {
      body = (await response.json()) as { message?: string; error?: string };
    } catch {
      // Infrastructure errors may not return JSON.
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

export function listTelemetryEvents(filters: TelemetryEventFilters, session?: Session | null) {
  const params = new URLSearchParams();
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  setParam(params, "source", filters.source);
  setParam(params, "status", filters.status);
  setParam(params, "sensorId", filters.sensorId);
  setParam(params, "producerId", filters.producerId);
  setParam(params, "messageId", filters.messageId);
  setParam(params, "from", filters.from);
  setParam(params, "to", filters.to);
  return telemetryRequest<RawTelemetryEventPage>(`/api/telemetry/events?${params.toString()}`, {}, session);
}

export function getTelemetryEvent(id: string, session?: Session | null) {
  return telemetryRequest<RawTelemetryEvent>(`/api/telemetry/events/${encodeURIComponent(id)}`, {}, session);
}

export function reprocessTelemetryEvent(id: string, reason: string, session?: Session | null) {
  return telemetryRequest<ReprocessTelemetryResponse>(
    `/api/telemetry/events/${encodeURIComponent(id)}/reprocess`,
    { method: "POST", body: JSON.stringify({ reason }) },
    session,
  );
}

function setParam(params: URLSearchParams, key: string, value?: string) {
  const normalized = value?.trim();
  if (normalized) {
    params.set(key, normalized);
  }
}
