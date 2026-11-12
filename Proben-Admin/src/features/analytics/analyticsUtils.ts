import { ApiError } from "../../lib/api";
import type {
  NumericBucket,
  NumericBucketFilters,
} from "../../types/analytics";

export const ANALYTICS_ROLES = ["ADMIN", "OPERADOR", "ANALISTA"] as const;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100, 200] as const;

export function toDateTimeLocal(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function defaultAnalyticsFilters(): Required<
  Pick<NumericBucketFilters, "from" | "to" | "page" | "size">
> {
  const to = new Date();
  const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
  return {
    from: toDateTimeLocal(from),
    to: toDateTimeLocal(to),
    page: 0,
    size: 100,
  };
}

export function cleanAnalyticsFilters(
  filters: NumericBucketFilters,
): NumericBucketFilters {
  const { from, to } = resolvePeriod(filters.from, filters.to);
  return {
    from,
    to,
    sensorExternalId: filters.sensorExternalId || undefined,
    sensorTypeName: filters.sensorTypeName || undefined,
    parametroDefId: filters.parametroDefId || undefined,
    compartimentoId: filters.compartimentoId || undefined,
    aggregationVersion: filters.aggregationVersion,
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  };
}

export function summaryFilters(filters: NumericBucketFilters) {
  return {
    from: filters.from,
    to: filters.to,
    sensorExternalId: filters.sensorExternalId,
    sensorTypeName: filters.sensorTypeName,
    parametroDefId: filters.parametroDefId,
    compartimentoId: filters.compartimentoId,
    aggregationVersion: filters.aggregationVersion,
  };
}

export function validatePeriod(from: string, to: string) {
  if ((from && Number.isNaN(new Date(from).getTime())) || (to && Number.isNaN(new Date(to).getTime()))) {
    return "Informe datas validas.";
  }
  if (from && to && new Date(from).getTime() >= new Date(to).getTime()) {
    return "A data inicial deve ser anterior a data final.";
  }
  if (from && to && new Date(to).getTime() - new Date(from).getTime() > 366 * 24 * 60 * 60 * 1000) {
    return "O periodo maximo para consulta e de 366 dias.";
  }
  return "";
}

/** Completes an optional/one-sided date range with a 30-day window. */
export function resolvePeriod(from: string, to: string) {
  const windowMs = 30 * 24 * 60 * 60 * 1000;
  const end = to ? new Date(to) : from ? new Date(new Date(from).getTime() + windowMs) : new Date();
  const start = from ? new Date(from) : new Date(end.getTime() - windowMs);
  return { from: start.toISOString(), to: end.toISOString() };
}

export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

export function formatNumber(value?: number | null, unit?: string | null) {
  if (value === null || value === undefined) return "-";
  const formatted = new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 6,
  }).format(value);
  return unit ? `${formatted} ${unit}` : formatted;
}

export function bucketSeriesLabel(bucket: NumericBucket) {
  const sensor = bucket.sensorNome || bucket.sensorExternalId;
  return `${sensor} / ${bucket.parametroNome} / v${bucket.aggregationVersion}`;
}

export function analyticsErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Filtros invalidos. Revise periodo, paginacao e versao de agregacao.";
    }
    if (error.status === 401) {
      return "Sessao expirada ou ausente. Entre novamente.";
    }
    if (error.status === 403) {
      return "Voce nao tem permissao para consultar analises.";
    }
    if (error.status === 422) {
      return "Um dos filtros informados nao existe no catalogo.";
    }
    if (error.status >= 500) {
      return "Falha interna ao consultar os buckets analiticos.";
    }
  }
  return error instanceof Error
    ? error.message
    : "Nao foi possivel carregar a analise.";
}
