export type TelemetrySource = "REST" | "MQTT";

export type RawTelemetryStatus =
  | "RECEIVED"
  | "PROCESSING"
  | "CANONICAL_ACCEPTED"
  | "CANONICAL_DUPLICATE"
  | "CANONICAL_CONFLICT"
  | "CANONICAL_FAILED"
  | "DISCARDED";

export interface RawTelemetryProcessing {
  attempts?: number;
  lastAttemptAt?: string;
  nextAttemptAt?: string;
  lockedAt?: string;
  workerId?: string;
  lastError?: string;
  canonicalMeasurementId?: string;
  profileId?: string;
  parserVersionId?: string;
}

export interface RawTelemetryReprocessing {
  count?: number;
  lastRequestedAt?: string;
  lastRequestedBy?: string;
  lastReason?: string;
}

export interface RawTelemetryReprocessAuditEntry {
  previousStatus: RawTelemetryStatus;
  lastError?: string;
  attempts: number;
  canonicalMeasurementId?: string;
  profileId?: string;
  parserVersionId?: string;
  requestedBy: string;
  requestedAt: string;
  reason: string;
}

export interface RawTelemetryEvent {
  id: string;
  producerId: string;
  source: TelemetrySource;
  messageId: string;
  sensorId?: string;
  sourceTimestamp?: string;
  receivedAt: string;
  payload: unknown;
  payloadHash: string;
  status: RawTelemetryStatus;
  processing?: RawTelemetryProcessing;
  reprocessing?: RawTelemetryReprocessing;
  reprocessAudit?: RawTelemetryReprocessAuditEntry[];
  expiresAt?: string;
}

export interface RawTelemetryEventPage {
  content: RawTelemetryEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TelemetryEventFilters {
  source?: TelemetrySource | "";
  status?: RawTelemetryStatus | "";
  sensorId?: string;
  producerId?: string;
  messageId?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export interface ReprocessTelemetryResponse {
  id: string;
  status: RawTelemetryStatus;
  previousStatus: RawTelemetryStatus;
  reprocessCount: number;
  requestedBy: string;
  requestedAt: string;
}
