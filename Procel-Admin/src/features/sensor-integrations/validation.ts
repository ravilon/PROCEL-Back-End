import type { ParserVersionRequest } from "../../types/sensorIntegrations";

export const MAX_MAPPINGS = 100;

export function isJsonPointer(value: string) {
  if (!value.startsWith("/")) return false;
  return !/(~(?![01]))/.test(value);
}

export function pointerError(value: string, required: boolean) {
  const trimmed = value.trim();
  if (!trimmed) return required ? "Campo obrigatorio" : "";
  return isJsonPointer(trimmed) ? "" : "JSON Pointer invalido";
}

export function toNullable(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

export function validateParserRequest(request: ParserVersionRequest) {
  const errors: string[] = [];
  if (pointerError(request.messageIdPointer, true)) errors.push("messageIdPointer invalido");
  if (pointerError(request.timestampPointer, true)) errors.push("timestampPointer invalido");
  if (request.sourceReceivedAtPointer && pointerError(request.sourceReceivedAtPointer, false)) {
    errors.push("sourceReceivedAtPointer invalido");
  }
  if (request.sensorResolutionMode === "PAYLOAD_POINTER") {
    if (!request.sensorExternalIdPointer || pointerError(request.sensorExternalIdPointer, true)) {
      errors.push("sensorExternalIdPointer invalido");
    }
  }
  if (request.sensorResolutionMode === "ROUTE_SENSOR" && request.sensorExternalIdPointer) {
    errors.push("sensorExternalIdPointer deve ficar vazio em ROUTE_SENSOR");
  }
  if (request.valueMappings.length === 0) errors.push("Informe ao menos um mapping");
  if (request.valueMappings.length > MAX_MAPPINGS) errors.push("Limite de 100 mappings excedido");

  const names = new Set<string>();
  for (const mapping of request.valueMappings) {
    const name = mapping.parameterName.trim();
    if (!name) errors.push("parameterName obrigatorio");
    if (name && names.has(name)) errors.push("parameterName duplicado");
    names.add(name);
    if (pointerError(mapping.valuePointer, true)) errors.push("valuePointer invalido");
  }
  return [...new Set(errors)];
}
