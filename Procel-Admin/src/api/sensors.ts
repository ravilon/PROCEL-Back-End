import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type { Medicao, Sensor, TipoSensor } from "../types/sensors";

export interface MeasurementFilters {
  from: string;
  to: string;
  page?: number;
  limit?: number;
}

function measurementQuery(filters: MeasurementFilters) {
  return new URLSearchParams({
    from: new Date(filters.from).toISOString(),
    to: new Date(filters.to).toISOString(),
    page: String(filters.page ?? 0),
    limit: String(filters.limit ?? 24),
  }).toString();
}

export function listSensorTypes(session?: Session | null, includeHidden?: boolean) {
  const suffix = includeHidden === undefined ? "" : `?includeHidden=${includeHidden}`;
  return apiRequest<TipoSensor[]>(`/api/sensor-admin/types${suffix}`, {}, session);
}

export function createSensor(
  payload: { externalId: string; nome: string; tipoNome: string; compartimentoId: string },
  session?: Session | null,
) {
  return apiRequest<Sensor>(
    "/api/sensor-admin/sensors",
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function hideSensor(externalId: string, session?: Session | null) {
  return apiRequest<void>(
    `/api/sensor-admin/sensors/${encodeURIComponent(externalId)}`,
    { method: "DELETE" },
    session,
  );
}

export function restoreSensor(externalId: string, session?: Session | null) {
  return apiRequest<Sensor>(
    `/api/sensor-admin/sensors/${encodeURIComponent(externalId)}/restore`,
    { method: "POST" },
    session,
  );
}

export function listMedicoesBySensor(
  sensorExternalId: string,
  filters: MeasurementFilters,
  session?: Session | null,
) {
  return apiRequest<Medicao[]>(
    `/api/sensors/${encodeURIComponent(sensorExternalId)}/medicoes?${measurementQuery(filters)}`,
    {},
    session,
  );
}

export function listMedicoesByCompartimento(
  compartimentoId: string,
  filters: MeasurementFilters,
  session?: Session | null,
) {
  return apiRequest<Medicao[]>(
    `/api/rooms/${encodeURIComponent(compartimentoId)}/medicoes?${measurementQuery(filters)}`,
    {},
    session,
  );
}
