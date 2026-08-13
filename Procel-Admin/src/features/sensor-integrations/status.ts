import type { ParserStatus } from "../../types/sensorIntegrations";

export function statusColor(status: ParserStatus) {
  if (status === "ACTIVE") return "success" as const;
  if (status === "DRAFT") return "warning" as const;
  return "default" as const;
}

export function activeLabel(active: boolean) {
  return active ? "Ativo" : "Inativo";
}
