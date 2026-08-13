import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type { Sensor } from "../types/sensors";
import type {
  BindingCreateRequest,
  BindingResponse,
  ParserVersionActivationRequest,
  ParserVersionRequest,
  ParserVersionResponse,
  ProfileCreateRequest,
  ProfileResponse,
  ProfileUpdateRequest,
  SnapshotResponse,
} from "../types/sensorIntegrations";

const base = "/api/sensor-integrations";

export function listIntegrationProfiles(includeInactive: boolean, session?: Session | null) {
  return apiRequest<ProfileResponse[]>(
    `${base}/profiles?includeInactive=${includeInactive}`,
    {},
    session,
  );
}

export function getIntegrationProfile(profileId: string, session?: Session | null) {
  return apiRequest<ProfileResponse>(`${base}/profiles/${profileId}`, {}, session);
}

export function createIntegrationProfile(payload: ProfileCreateRequest, session?: Session | null) {
  return apiRequest<ProfileResponse>(
    `${base}/profiles`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function updateIntegrationProfile(
  profileId: string,
  payload: ProfileUpdateRequest,
  session?: Session | null,
) {
  return apiRequest<ProfileResponse>(
    `${base}/profiles/${profileId}`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
}

export function activateIntegrationProfile(profileId: string, session?: Session | null) {
  return apiRequest<ProfileResponse>(
    `${base}/profiles/${profileId}/activate`,
    { method: "POST" },
    session,
  );
}

export function deactivateIntegrationProfile(profileId: string, session?: Session | null) {
  return apiRequest<void>(
    `${base}/profiles/${profileId}`,
    { method: "DELETE" },
    session,
  );
}

export function createParserVersion(
  profileId: string,
  payload: ParserVersionRequest,
  session?: Session | null,
) {
  return apiRequest<ParserVersionResponse>(
    `${base}/profiles/${profileId}/versions`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function listParserVersions(profileId: string, session?: Session | null) {
  return apiRequest<ParserVersionResponse[]>(`${base}/profiles/${profileId}/versions`, {}, session);
}

export function updateParserVersion(
  profileId: string,
  versionId: string,
  payload: ParserVersionRequest,
  session?: Session | null,
) {
  return apiRequest<ParserVersionResponse>(
    `${base}/profiles/${profileId}/versions/${versionId}`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
}

export function activateParserVersion(
  profileId: string,
  versionId: string,
  payload: ParserVersionActivationRequest,
  session?: Session | null,
) {
  return apiRequest<ParserVersionResponse>(
    `${base}/profiles/${profileId}/versions/${versionId}/activate`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function createIntegrationBinding(
  profileId: string,
  payload: BindingCreateRequest,
  session?: Session | null,
) {
  return apiRequest<BindingResponse>(
    `${base}/profiles/${profileId}/bindings`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function listIntegrationBindings(
  profileId: string,
  includeInactive: boolean,
  session?: Session | null,
) {
  return apiRequest<BindingResponse[]>(
    `${base}/profiles/${profileId}/bindings?includeInactive=${includeInactive}`,
    {},
    session,
  );
}

export function activateIntegrationBinding(bindingId: string, session?: Session | null) {
  return apiRequest<BindingResponse>(
    `${base}/bindings/${bindingId}/activate`,
    { method: "POST" },
    session,
  );
}

export function deactivateIntegrationBinding(bindingId: string, session?: Session | null) {
  return apiRequest<void>(
    `${base}/bindings/${bindingId}`,
    { method: "DELETE" },
    session,
  );
}

export function getIntegrationSnapshot(session?: Session | null) {
  return apiRequest<SnapshotResponse>(`${base}/snapshot`, {}, session);
}

export function searchCatalogSensors(query: string, session?: Session | null) {
  return apiRequest<Sensor[]>(
    `/api/catalog/sensores?${new URLSearchParams({
      q: query,
      includeHidden: "false",
    }).toString()}`,
    {},
    session,
  );
}
