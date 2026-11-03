import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type {
  NumericBucketFilters,
  NumericBucketPage,
  NumericBucketSummary,
  NumericBucketSummaryFilters,
} from "../types/analytics";

function setOptionalParam(
  params: URLSearchParams,
  key: string,
  value: string | number | undefined,
) {
  if (value === undefined) return;
  if (typeof value === "string" && value.trim() === "") return;
  params.set(key, String(value));
}

function analyticsQuery(filters: NumericBucketFilters) {
  const params = new URLSearchParams();
  params.set("from", new Date(filters.from).toISOString());
  params.set("to", new Date(filters.to).toISOString());
  setOptionalParam(params, "sensorExternalId", filters.sensorExternalId);
  setOptionalParam(params, "parametroDefId", filters.parametroDefId);
  setOptionalParam(params, "compartimentoId", filters.compartimentoId);
  setOptionalParam(params, "aggregationVersion", filters.aggregationVersion);
  setOptionalParam(params, "page", filters.page);
  setOptionalParam(params, "size", filters.size);
  return params.toString();
}

function summaryQuery(filters: NumericBucketSummaryFilters) {
  const params = new URLSearchParams();
  params.set("from", new Date(filters.from).toISOString());
  params.set("to", new Date(filters.to).toISOString());
  setOptionalParam(params, "sensorExternalId", filters.sensorExternalId);
  setOptionalParam(params, "parametroDefId", filters.parametroDefId);
  setOptionalParam(params, "compartimentoId", filters.compartimentoId);
  setOptionalParam(params, "aggregationVersion", filters.aggregationVersion);
  return params.toString();
}

export function listNumericBuckets(
  filters: NumericBucketFilters,
  session?: Session | null,
) {
  return apiRequest<NumericBucketPage>(
    `/api/analytics/numeric-buckets?${analyticsQuery(filters)}`,
    {},
    session,
  );
}

export function getNumericBucketSummary(
  filters: NumericBucketSummaryFilters,
  session?: Session | null,
) {
  return apiRequest<NumericBucketSummary[]>(
    `/api/analytics/numeric-buckets/summary?${summaryQuery(filters)}`,
    {},
    session,
  );
}
