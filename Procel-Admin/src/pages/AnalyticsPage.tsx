import { InsightsOutlined } from "@mui/icons-material";
import { Alert, Box, CircularProgress, LinearProgress, Stack, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { getNumericBucketSummary, listNumericBuckets } from "../api/analytics";
import { listCompartimentos } from "../api/catalog";
import { searchCatalogSensors } from "../api/sensorIntegrations";
import { listSensorTypes } from "../api/sensors";
import { useAuth } from "../auth/AuthContext";
import { AnalyticsBucketsTable } from "../features/analytics/AnalyticsBucketsTable";
import { AnalyticsEmptyState } from "../features/analytics/AnalyticsEmptyState";
import {
  AnalyticsFilters,
  type AnalyticsFilterState,
} from "../features/analytics/AnalyticsFilters";
import { AnalyticsSummaryCards } from "../features/analytics/AnalyticsSummaryCards";
import { AnalyticsTimeSeriesChart } from "../features/analytics/AnalyticsTimeSeriesChart";
import {
  analyticsErrorMessage,
  cleanAnalyticsFilters,
  defaultAnalyticsFilters,
  summaryFilters,
  validatePeriod,
} from "../features/analytics/analyticsUtils";
import type { NumericBucketFilters, ParametroDef } from "../types";

export function AnalyticsPage() {
  const { session } = useAuth();
  const defaults = useMemo(() => defaultAnalyticsFilters(), []);
  const [draftFilters, setDraftFilters] = useState<AnalyticsFilterState>(defaults);
  const [appliedFilters, setAppliedFilters] = useState<NumericBucketFilters>(
    cleanAnalyticsFilters(defaults),
  );
  const [validationError, setValidationError] = useState("");

  const sensors = useQuery({
    queryKey: ["analytics", "catalog", "sensors"],
    queryFn: () => searchCatalogSensors("", session),
    enabled: Boolean(session),
  });
  const sensorTypes = useQuery({
    queryKey: ["analytics", "catalog", "sensor-types"],
    queryFn: () => listSensorTypes(session, false),
    enabled: Boolean(session),
  });
  const rooms = useQuery({
    queryKey: ["analytics", "catalog", "compartimentos"],
    queryFn: () => listCompartimentos({ q: "" }, session),
    enabled: Boolean(session),
  });

  const parametros = useMemo<ParametroDef[]>(() => {
    const byId = new Map<string, ParametroDef>();
    for (const type of sensorTypes.data ?? []) {
      for (const parametro of type.parametros) {
        if (parametro.dataType === "NUMERIC" && parametro.ativo) {
          byId.set(parametro.id, parametro);
        }
      }
    }
    return Array.from(byId.values()).sort((a, b) => a.nome.localeCompare(b.nome));
  }, [sensorTypes.data]);

  const buckets = useQuery({
    queryKey: ["analytics", "numeric-buckets", appliedFilters],
    queryFn: () => listNumericBuckets(appliedFilters, session),
    enabled: Boolean(session) && !validatePeriod(appliedFilters.from, appliedFilters.to),
    placeholderData: (previous) => previous,
  });
  const summary = useQuery({
    queryKey: ["analytics", "numeric-buckets", "summary", summaryFilters(appliedFilters)],
    queryFn: () => getNumericBucketSummary(summaryFilters(appliedFilters), session),
    enabled: Boolean(session) && !validatePeriod(appliedFilters.from, appliedFilters.to),
    placeholderData: (previous) => previous,
  });

  const applyFilters = () => {
    const error = validatePeriod(draftFilters.from, draftFilters.to);
    setValidationError(error);
    if (error) return;
    setAppliedFilters(cleanAnalyticsFilters({ ...draftFilters, page: 0 }));
  };

  const clearOptionalFilters = () => {
    const next = {
      from: draftFilters.from,
      to: draftFilters.to,
      page: 0,
      size: draftFilters.size ?? 20,
      aggregationVersionText: "",
    };
    setDraftFilters(next);
    setAppliedFilters(cleanAnalyticsFilters(next));
    setValidationError("");
  };

  const changePage = (page: number) => {
    setAppliedFilters((current) => ({ ...current, page }));
  };

  const catalogError = sensors.error ?? sensorTypes.error ?? rooms.error;
  const bucketPage = buckets.data;
  const hasNoData =
    !buckets.isLoading &&
    !buckets.isError &&
    bucketPage?.content.length === 0 &&
    bucketPage.totalElements === 0;
  const initialLoading = buckets.isLoading && !bucketPage;

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <InsightsOutlined color="primary" fontSize="large" />
          <Box>
            <Typography variant="h4">Análises</Typography>
            <Typography color="text.secondary">
              Explore buckets numericos calculados pela API analitica.
            </Typography>
          </Box>
        </Stack>
      </Box>

      <AnalyticsFilters
        value={draftFilters}
        sensors={sensors.data ?? []}
        parametros={parametros}
        compartimentos={rooms.data ?? []}
        loadingCatalogs={sensors.isLoading || sensorTypes.isLoading || rooms.isLoading}
        onChange={(filters) => {
          setDraftFilters(filters);
          setValidationError("");
        }}
        onApply={applyFilters}
        onClearOptional={clearOptionalFilters}
      />

      {validationError && <Alert severity="warning">{validationError}</Alert>}
      {catalogError && (
        <Alert severity="warning">
          Nao foi possivel carregar todos os filtros de catalogo:{" "}
          {analyticsErrorMessage(catalogError)}
        </Alert>
      )}
      {(buckets.isFetching || summary.isFetching) && !initialLoading && (
        <Box aria-live="polite">
          <LinearProgress />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Atualizando analise...
          </Typography>
        </Box>
      )}
      {initialLoading && (
        <Stack direction="row" spacing={1.5} alignItems="center" aria-live="polite">
          <CircularProgress size={24} />
          <Typography>Carregando analise...</Typography>
        </Stack>
      )}
      {buckets.isError && (
        <Alert severity="error">{analyticsErrorMessage(buckets.error)}</Alert>
      )}
      {summary.isError && (
        <Alert severity="error">{analyticsErrorMessage(summary.error)}</Alert>
      )}

      {!buckets.isError && (
        <>
          <AnalyticsSummaryCards
            summaries={summary.data ?? []}
            loading={summary.isFetching}
          />
          {hasNoData ? (
            <AnalyticsEmptyState message="Filtros existentes, mas sem buckets compativeis no periodo informado." />
          ) : (
            <>
              <AnalyticsTimeSeriesChart buckets={bucketPage?.content ?? []} />
              <AnalyticsBucketsTable
                page={bucketPage}
                loading={buckets.isFetching}
                onPageChange={changePage}
              />
            </>
          )}
        </>
      )}
    </Stack>
  );
}
