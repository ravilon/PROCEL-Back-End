export type MedicaoIngestaoSource = "MQTT" | "REST" | "FILE" | "API";
export type SensorResolutionMode = "ROUTE_SENSOR" | "PAYLOAD_POINTER";
export type ParserStatus = "DRAFT" | "ACTIVE" | "INACTIVE";

export interface ProfileCreateRequest {
  nome: string;
  descricao: string | null;
  source: MedicaoIngestaoSource;
}

export interface ProfileUpdateRequest {
  nome: string;
  descricao: string | null;
  source: MedicaoIngestaoSource | null;
}

export interface ProfileResponse {
  id: string;
  nome: string;
  descricao: string | null;
  source: MedicaoIngestaoSource;
  ativo: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ValueMappingRequest {
  parameterName: string;
  valuePointer: string;
  required: boolean;
}

export interface ValueMappingResponse {
  id: string;
  parameterName: string;
  valuePointer: string;
  required: boolean;
}

export interface ParserVersionRequest {
  sensorResolutionMode: SensorResolutionMode;
  messageIdPointer: string;
  sensorExternalIdPointer: string | null;
  timestampPointer: string;
  sourceReceivedAtPointer: string | null;
  timestampFormat: "ISO_INSTANT";
  valueMappings: ValueMappingRequest[];
}

export interface ParserVersionResponse {
  id: string;
  profileId: string;
  version: number;
  status: ParserStatus;
  sensorResolutionMode: SensorResolutionMode;
  messageIdPointer: string;
  sensorExternalIdPointer: string | null;
  timestampPointer: string;
  sourceReceivedAtPointer: string | null;
  timestampFormat: string;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  valueMappings: ValueMappingResponse[];
}

export interface ParserVersionActivationRequest {
  expectedActiveVersionId: string | null;
}

export interface BindingCreateRequest {
  sensorExternalId: string;
}

export interface BindingResponse {
  id: string;
  profileId: string;
  sensorExternalId: string;
  sensorNome: string;
  ativo: boolean;
  createdAt: string;
  deactivatedAt: string | null;
}

export interface SnapshotResponse {
  version: number;
  generatedAt: string;
  profiles: SnapshotProfile[];
}

export interface SnapshotProfile {
  id: string;
  nome: string;
  source: MedicaoIngestaoSource;
  activeParserVersion: SnapshotParserVersion;
  bindings: SnapshotBinding[];
}

export interface SnapshotParserVersion {
  id: string;
  version: number;
  sensorResolutionMode: SensorResolutionMode;
  messageIdPointer: string;
  sensorExternalIdPointer: string | null;
  timestampPointer: string;
  sourceReceivedAtPointer: string | null;
  timestampFormat: string;
  valueMappings: SnapshotMapping[];
}

export interface SnapshotMapping {
  parameterName: string;
  valuePointer: string;
  required: boolean;
}

export interface SnapshotBinding {
  sensorExternalId: string;
  sensorNome: string;
}
