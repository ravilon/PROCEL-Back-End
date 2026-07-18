import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type { GrupoRegra } from "../types/sensors";

export function listRuleGroups(session?: Session | null) {
  return apiRequest<GrupoRegra[]>("/api/rules/groups", {}, session);
}

export function assignRuleGroupToRooms(
  grupoRegraId: string,
  payload: {
    compartimentoIds: string[];
    status: string;
    validoDe: string | null;
    validoAte: string | null;
  },
  session?: Session | null,
) {
  return apiRequest<unknown>(
    `/api/rules/groups/${grupoRegraId}/rooms`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}
