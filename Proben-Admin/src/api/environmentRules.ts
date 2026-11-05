import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type {
  EnvironmentRule,
  EnvironmentRuleExecution,
  EnvironmentRuleInput,
  EnvironmentRuleResolution,
  EnvironmentRuleTestResult,
} from "../types/environmentRules";

export const environmentRulesApi = {
  list: (query: URLSearchParams, session?: Session | null) =>
    apiRequest<EnvironmentRule[]>(
      `/api/environment-rules?${query}`,
      {},
      session,
    ),
  create: (input: EnvironmentRuleInput, session?: Session | null) =>
    apiRequest<EnvironmentRule>(
      "/api/environment-rules",
      { method: "POST", body: JSON.stringify(input) },
      session,
    ),
  update: (id: string, input: EnvironmentRuleInput, session?: Session | null) =>
    apiRequest<EnvironmentRule>(
      `/api/environment-rules/${id}`,
      { method: "PUT", body: JSON.stringify(input) },
      session,
    ),
  setActive: (id: string, active: boolean, session?: Session | null) =>
    apiRequest<EnvironmentRule>(
      `/api/environment-rules/${id}/${active ? "activate" : "deactivate"}`,
      { method: "POST" },
      session,
    ),
  duplicate: (id: string, session?: Session | null) =>
    apiRequest<EnvironmentRule>(
      `/api/environment-rules/${id}/duplicate`,
      { method: "POST" },
      session,
    ),
  copy: (id: string, rooms: string[], session?: Session | null) =>
    apiRequest<EnvironmentRule[]>(
      `/api/environment-rules/${id}/copy`,
      { method: "POST", body: JSON.stringify({ compartimentoIds: rooms }) },
      session,
    ),
  resolve: (room: string, session?: Session | null) =>
    apiRequest<EnvironmentRuleResolution[]>(
      `/api/environment-rules/resolution/${encodeURIComponent(room)}`,
      {},
      session,
    ),
  test: (id: string, room: string, session?: Session | null) =>
    apiRequest<EnvironmentRuleTestResult>(
      `/api/environment-rules/${id}/test`,
      { method: "POST", body: JSON.stringify({ compartimentoId: room }) },
      session,
    ),
  execute: (id: string, room: string, session?: Session | null) =>
    apiRequest<EnvironmentRuleExecution>(
      `/api/environment-rules/${id}/execute?roomId=${encodeURIComponent(room)}`,
      { method: "POST" },
      session,
    ),
  executions: (ruleId: string | null, session?: Session | null) =>
    apiRequest<EnvironmentRuleExecution[]>(
      `/api/environment-rules/executions${ruleId ? `?ruleId=${ruleId}` : ""}`,
      {},
      session,
    ),
  deletePreview: (id: string, session?: Session | null) =>
    apiRequest<{
      executionCount: number;
      deletable: boolean;
      recommendation: string;
    }>(`/api/environment-rules/${id}/delete-preview`, {}, session),
  nextRuns: (id: string, session?: Session | null) =>
    apiRequest<string[]>(`/api/environment-rules/${id}/next-runs`, {}, session),
  remove: (id: string, session?: Session | null) =>
    apiRequest<void>(
      `/api/environment-rules/${id}`,
      { method: "DELETE" },
      session,
    ),
};
