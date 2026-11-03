import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";

export interface Campus {
  nome: string;
  latitude?: number;
  longitude?: number;
  contextoNomes: string[];
}
export interface SharedContext {
  id: string;
  nome: string;
  descricao?: string;
  ativo: boolean;
  latitude?: number;
  longitude?: number;
  timezone: string;
  locationMode: "OWN" | "CAMPUS";
  campusNome?: string;
  resolvedLatitude?: number;
  resolvedLongitude?: number;
  compartimentoIds: string[];
}
export interface ContextDeletePreview {
  id: string;
  nome: string;
  compartimentos: number;
  sensores: number;
  integracoes: number;
  fontesSelecionadas: number;
  medicoes: number;
  auditorias: number;
  deletable: boolean;
  dependencies: string[];
}
export interface HttpMapping {
  parametroDefId: string;
  parameterName: string;
  jsonPointer: string;
  expectedType: "NUMERIC" | "BOOLEAN" | "TEXT";
  sourceUnit?: string;
  targetUnit?: string;
  required: boolean;
  conversion: "NONE" | "C_TO_F" | "F_TO_C";
}
export interface SharedIntegration {
  id: string;
  contextoId: string;
  nome: string;
  descricao?: string;
  providerName?: string;
  attribution?: string;
  sensorExternalId: string;
  ativo: boolean;
  endpointUrl: string;
  metodo: "GET" | "POST";
  contentType: string;
  requestBody?: string;
  authMode: "NONE" | "HEADER" | "QUERY" | "BEARER";
  credentialConfigured: boolean;
  credentialHeader?: string;
  credentialQueryParam?: string;
  coordinateQueryParam?: string;
  queryParamsJson?: string;
  headersJson?: string;
  mappings: HttpMapping[];
  temperatureJsonPointer?: string;
  observedAtJsonPointer?: string;
  observedAtFormat:
    "ISO_8601" | "UNIX_SECONDS" | "UNIX_MILLISECONDS" | "COLLECTION_TIME";
  observedAtTimezone?: string;
  sourceIdJsonPointer?: string;
  intervalSeconds: number;
  timeoutMillis: number;
  maxAttempts: number;
  retryDelayMillis: number;
  validitySeconds: number;
  automaticCollection: boolean;
  nextRunAt?: string;
  lastSuccessAt?: string;
  lastOutcome?: string;
  lastObservationAt?: string;
  lastError?: string;
}
export interface IntegrationPayload {
  sensorExternalId: string;
  nome: string;
  descricao?: string;
  providerName?: string;
  attribution?: string;
  ativo: boolean;
  endpointUrl: string;
  metodo: string;
  contentType: string;
  requestBody?: string;
  authMode: string;
  credential?: string;
  credentialHeader?: string;
  credentialQueryParam?: string;
  coordinateQueryParam?: string;
  queryParamsJson?: string;
  headersJson?: string;
  mappings: HttpMapping[];
  temperatureJsonPointer?: string;
  observedAtJsonPointer?: string;
  observedAtFormat: string;
  observedAtTimezone?: string;
  sourceIdJsonPointer?: string;
  intervalSeconds: number;
  timeoutMillis: number;
  maxAttempts: number;
  retryDelayMillis: number;
  validitySeconds: number;
  automaticCollection: boolean;
}
export interface MappingTestResult {
  parametroDefId?: string;
  parameterName: string;
  jsonPointer: string;
  foundValue?: string;
  detectedType?: string;
  convertedValue?: string;
  unit?: string;
  success: boolean;
  error?: string;
}
export interface CollectionTestResult {
  outcome: string;
  observationAt?: string;
  value?: number;
  persisted: boolean;
  httpStatus?: number;
  durationMillis?: number;
  method?: string;
  sanitizedUrl?: string;
  headers: Record<string, string>;
  queryParameters: Record<string, string>;
  rawJson?: string;
  mappings: MappingTestResult[];
  observedAtOriginal?: string;
  observedAtFormat?: string;
  errorType?: string;
  errorMessage?: string;
}
export const listCampuses = (session?: Session | null) =>
  apiRequest<Campus[]>("/api/campuses", {}, session);
export const updateCampus = (
  nome: string,
  payload: object,
  session?: Session | null,
) =>
  apiRequest<Campus>(
    `/api/campuses/${encodeURIComponent(nome)}`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
export const listSharedContexts = (session?: Session | null) =>
  apiRequest<SharedContext[]>("/api/shared-contexts", {}, session);
export const createSharedContext = (
  payload: object,
  session?: Session | null,
) =>
  apiRequest<SharedContext>(
    "/api/shared-contexts",
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
export const updateSharedContext = (
  id: string,
  payload: object,
  session?: Session | null,
) =>
  apiRequest<SharedContext>(
    `/api/shared-contexts/${id}`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
export const getSharedContextDeletePreview = (
  id: string,
  session?: Session | null,
) =>
  apiRequest<ContextDeletePreview>(
    `/api/shared-contexts/${id}/delete-preview`,
    {},
    session,
  );
export const deleteSharedContext = (id: string, session?: Session | null) =>
  apiRequest<void>(`/api/shared-contexts/${id}`, { method: "DELETE" }, session);
export const replaceContextRooms = (
  id: string,
  compartimentoIds: string[],
  session?: Session | null,
) =>
  apiRequest<SharedContext>(
    `/api/shared-contexts/${id}/rooms`,
    { method: "PUT", body: JSON.stringify({ compartimentoIds }) },
    session,
  );
export const listSharedIntegrations = (id: string, session?: Session | null) =>
  apiRequest<SharedIntegration[]>(
    `/api/shared-contexts/${id}/integrations`,
    {},
    session,
  );
export const saveSharedIntegration = (
  contextId: string,
  payload: IntegrationPayload,
  session?: Session | null,
) =>
  apiRequest<SharedIntegration>(
    `/api/shared-contexts/${contextId}/integration`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
export const testSharedIntegrationConfiguration = (
  contextId: string,
  payload: IntegrationPayload,
  session?: Session | null,
) =>
  apiRequest<CollectionTestResult>(
    `/api/shared-contexts/${contextId}/integration/test`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
export const testSharedIntegration = (id: string, session?: Session | null) =>
  apiRequest<CollectionTestResult>(
    `/api/shared-contexts/integrations/${id}/test`,
    { method: "POST" },
    session,
  );
export const collectSharedIntegration = (
  id: string,
  session?: Session | null,
) =>
  apiRequest<CollectionTestResult>(
    `/api/shared-contexts/integrations/${id}/collect`,
    { method: "POST" },
    session,
  );
export const setSharedIntegrationActive = (
  id: string,
  active: boolean,
  session?: Session | null,
) =>
  apiRequest<SharedIntegration>(
    `/api/shared-contexts/integrations/${id}/${active ? "activate" : "deactivate"}`,
    { method: "POST" },
    session,
  );
export const removeSharedIntegrationCredential = (
  id: string,
  session?: Session | null,
) =>
  apiRequest<SharedIntegration>(
    `/api/shared-contexts/integrations/${id}/credential`,
    { method: "DELETE" },
    session,
  );
export const selectSharedSourceBatch = (
  payload: object,
  session?: Session | null,
) =>
  apiRequest<void>(
    "/api/shared-contexts/rooms/source-batch",
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
