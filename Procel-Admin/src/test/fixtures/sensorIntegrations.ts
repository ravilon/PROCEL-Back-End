import type { Sensor } from "../../types/sensors";
import type {
  BindingResponse,
  ParserVersionResponse,
  ProfileResponse,
  SnapshotResponse,
} from "../../types/sensorIntegrations";

export const adminSession = {
  accessToken: "admin-token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "admin",
  email: "admin@example.com",
  roles: ["ADMIN" as const],
};

export const userSession = {
  accessToken: "user-token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "user",
  email: "user@example.com",
  roles: ["USUARIO" as const],
};

export const profile: ProfileResponse = {
  id: "profile-1",
  nome: "REST Energia",
  descricao: "Perfil REST",
  source: "REST",
  ativo: true,
  createdAt: "2026-01-01T10:00:00Z",
  updatedAt: "2026-01-02T10:00:00Z",
};

export const inactiveProfile: ProfileResponse = {
  ...profile,
  id: "profile-2",
  nome: "MQTT legado",
  descricao: "Perfil antigo",
  source: "MQTT",
  ativo: false,
};

export const draftVersion: ParserVersionResponse = {
  id: "version-draft",
  profileId: "profile-1",
  version: 2,
  status: "DRAFT",
  sensorResolutionMode: "ROUTE_SENSOR",
  messageIdPointer: "/messageId",
  sensorExternalIdPointer: null,
  timestampPointer: "/timestamp",
  sourceReceivedAtPointer: null,
  timestampFormat: "ISO_INSTANT",
  createdAt: "2026-01-03T10:00:00Z",
  updatedAt: "2026-01-03T10:00:00Z",
  publishedAt: null,
  valueMappings: [{ id: "map-1", parameterName: "temp", valuePointer: "/value", required: true }],
};

export const activeVersion: ParserVersionResponse = {
  ...draftVersion,
  id: "version-active",
  version: 1,
  status: "ACTIVE",
  publishedAt: "2026-01-02T10:00:00Z",
};

export const inactiveVersion: ParserVersionResponse = {
  ...draftVersion,
  id: "version-inactive",
  version: 0,
  status: "INACTIVE",
  publishedAt: "2026-01-01T10:00:00Z",
};

export const activeBinding: BindingResponse = {
  id: "binding-1",
  profileId: "profile-1",
  sensorExternalId: "sensor-active",
  sensorNome: "Sensor ativo",
  ativo: true,
  createdAt: "2026-01-04T10:00:00Z",
  deactivatedAt: null,
};

export const inactiveBinding: BindingResponse = {
  ...activeBinding,
  id: "binding-2",
  sensorExternalId: "sensor-old",
  sensorNome: "Sensor historico",
  ativo: false,
  deactivatedAt: "2026-01-05T10:00:00Z",
};

export const activeSensor: Sensor = {
  externalId: "sensor-active",
  nome: "Sensor ativo",
  tipoNome: "energia",
  compartimentoId: "room-1",
  compartimentoNome: "Sala 1",
  ativo: true,
};

export const inactiveSensor: Sensor = {
  ...activeSensor,
  externalId: "sensor-inactive",
  nome: "Sensor inativo",
  ativo: false,
};

export const snapshot: SnapshotResponse = {
  version: 1,
  generatedAt: "2026-01-06T10:00:00Z",
  profiles: [{
    id: "profile-1",
    nome: "REST Energia",
    source: "REST",
    activeParserVersion: {
      id: "version-active",
      version: 1,
      sensorResolutionMode: "ROUTE_SENSOR",
      messageIdPointer: "/messageId",
      sensorExternalIdPointer: null,
      timestampPointer: "/timestamp",
      sourceReceivedAtPointer: null,
      timestampFormat: "ISO_INSTANT",
      valueMappings: [{ parameterName: "temp", valuePointer: "/value", required: true }],
    },
    bindings: [{ sensorExternalId: "sensor-active", sensorNome: "Sensor ativo" }],
  }],
};
