import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type {
  EvaluationRequest,
  EventDefinitionSummary,
  EventoDefinicao,
  EventoOcorrenciaStatus,
  MissionEventFilters,
  MissionOccurrence,
  MissionOccurrenceFilters,
  MissionRequestFilters,
  MissionWindow,
  MissionWindowFilters,
  MissionWorkerStatus,
  OccurrenceEvidence,
  PageResponse,
  WindowEvidence,
  WorkerRunResponse,
} from "../types/missions";

function setParam(params: URLSearchParams, key: string, value?: string | number | boolean | null) {
  if (value === undefined || value === null || value === "") return;
  params.set(key, String(value));
}

function query(filters: object) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (
      typeof value === "string"
      || typeof value === "number"
      || typeof value === "boolean"
      || value === null
      || value === undefined
    ) {
      setParam(params, key, value);
    }
  });
  return params.toString();
}

export function listMissionEvents(filters: MissionEventFilters, session?: Session | null) {
  return apiRequest<PageResponse<EventDefinitionSummary>>(
    `/api/admin/missions/events?${query(filters)}`,
    {},
    session,
  );
}

export function getMissionEvent(eventId: string, session?: Session | null) {
  return apiRequest<EventoDefinicao>(
    `/api/mission-events/${encodeURIComponent(eventId)}`,
    {},
    session,
  );
}

export function listEvaluationRequests(filters: MissionRequestFilters, session?: Session | null) {
  return apiRequest<PageResponse<EvaluationRequest>>(
    `/api/admin/missions/evaluation-requests?${query(filters)}`,
    {},
    session,
  );
}

export function listMissionWindows(filters: MissionWindowFilters, session?: Session | null) {
  return apiRequest<PageResponse<MissionWindow>>(
    `/api/admin/missions/windows?${query(filters)}`,
    {},
    session,
  );
}

export function getMissionWindow(windowId: string, session?: Session | null) {
  return apiRequest<MissionWindow>(
    `/api/admin/missions/windows/${encodeURIComponent(windowId)}`,
    {},
    session,
  );
}

export function listWindowEvidence(windowId: string, page: number, size: number, session?: Session | null) {
  return apiRequest<PageResponse<WindowEvidence>>(
    `/api/admin/missions/windows/${encodeURIComponent(windowId)}/evidences?${query({ page, size })}`,
    {},
    session,
  );
}

export function operateMissionWindow(
  windowId: string,
  operation: "retry" | "satisfy" | "invalidate" | "expire" | "fail",
  payload: { reason?: string; retryAt?: string } = {},
  session?: Session | null,
) {
  const body = operation === "satisfy" ? undefined : JSON.stringify(payload);
  return apiRequest<MissionWindow>(
    `/api/admin/missions/windows/${encodeURIComponent(windowId)}/${operation}`,
    body ? { method: "POST", body } : { method: "POST" },
    session,
  );
}

export function listMissionOccurrences(filters: MissionOccurrenceFilters, session?: Session | null) {
  return apiRequest<PageResponse<MissionOccurrence>>(
    `/api/admin/missions/occurrences?${query(filters)}`,
    {},
    session,
  );
}

export function getMissionOccurrence(occurrenceId: string, session?: Session | null) {
  return apiRequest<MissionOccurrence>(
    `/api/admin/missions/occurrences/${encodeURIComponent(occurrenceId)}`,
    {},
    session,
  );
}

export function listOccurrenceEvidence(occurrenceId: string, page: number, size: number, session?: Session | null) {
  return apiRequest<PageResponse<OccurrenceEvidence>>(
    `/api/admin/missions/occurrences/${encodeURIComponent(occurrenceId)}/evidences?${query({ page, size })}`,
    {},
    session,
  );
}

export function updateOccurrenceStatus(
  occurrenceId: string,
  status: EventoOcorrenciaStatus,
  session?: Session | null,
) {
  return apiRequest<MissionOccurrence>(
    `/api/admin/missions/occurrences/${encodeURIComponent(occurrenceId)}/status`,
    { method: "POST", body: JSON.stringify({ status }) },
    session,
  );
}

export function getMissionWorkerStatus(session?: Session | null) {
  return apiRequest<MissionWorkerStatus>("/api/admin/missions/workers/status", {}, session);
}

export function runMissionWorker(worker: "evaluation" | "temporal-windows", session?: Session | null) {
  return apiRequest<WorkerRunResponse>(
    `/api/admin/missions/workers/${worker}/run`,
    { method: "POST" },
    session,
  );
}
