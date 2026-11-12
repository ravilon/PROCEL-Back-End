import { InsightsOutlined } from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { getNumericBucketSummary, listNumericBuckets } from "../api/analytics";
import { listCompartimentos } from "../api/catalog";
import {
  listMedicoesByCompartimento,
  listMedicoesBySensor,
} from "../api/sensors";
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
import { SharedObservationQuery } from "../features/analytics/SharedObservationQuery";
import {
  analyticsErrorMessage,
  cleanAnalyticsFilters,
  defaultAnalyticsFilters,
  summaryFilters,
  validatePeriod,
} from "../features/analytics/analyticsUtils";
import type { NumericBucketFilters, ParametroDef } from "../types";
import type { Medicao } from "../types/sensors";

export function AnalyticsPage() {
  const { session } = useAuth();
  const defaults = useMemo(() => defaultAnalyticsFilters(), []);
  const [draftFilters, setDraftFilters] =
    useState<AnalyticsFilterState>(defaults);
  const [appliedFilters, setAppliedFilters] = useState<NumericBucketFilters>(
    cleanAnalyticsFilters(defaults),
  );
  const [validationError, setValidationError] = useState("");
  const [resultMode, setResultMode] = useState<"buckets" | "measurements">(
    "buckets",
  );

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
    return Array.from(byId.values()).sort((a, b) =>
      a.nome.localeCompare(b.nome),
    );
  }, [sensorTypes.data]);

  const buckets = useQuery({
    queryKey: ["analytics", "numeric-buckets", appliedFilters],
    queryFn: () => listNumericBuckets(appliedFilters, session),
    enabled:
      Boolean(session) &&
      !validatePeriod(appliedFilters.from, appliedFilters.to),
    placeholderData: (previous) => previous,
  });
  const summary = useQuery({
    queryKey: [
      "analytics",
      "numeric-buckets",
      "summary",
      summaryFilters(appliedFilters),
    ],
    queryFn: () =>
      getNumericBucketSummary(summaryFilters(appliedFilters), session),
    enabled:
      Boolean(session) &&
      !validatePeriod(appliedFilters.from, appliedFilters.to),
    placeholderData: (previous) => previous,
  });
  const measurements = useQuery({
    queryKey: ["analytics", "measurements", appliedFilters],
    queryFn: () => {
      if (!appliedFilters.compartimentoId && !appliedFilters.sensorExternalId) {
        return Promise.resolve([] as Medicao[]);
      }
      const filters = {
        from: appliedFilters.from,
        to: appliedFilters.to,
        page: appliedFilters.page ?? 0,
        limit: appliedFilters.size ?? 20,
      };
      if (appliedFilters.sensorExternalId) {
        return listMedicoesBySensor(
          appliedFilters.sensorExternalId,
          filters,
          session,
        );
      }
      return listMedicoesByCompartimento(
        appliedFilters.compartimentoId!,
        filters,
        session,
      );
    },
    enabled:
      resultMode === "measurements" &&
      Boolean(
        session &&
          (appliedFilters.compartimentoId || appliedFilters.sensorExternalId),
      ) &&
      !validatePeriod(appliedFilters.from, appliedFilters.to),
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
  const selectedLoading =
    resultMode === "measurements" ? measurements.isLoading : buckets.isLoading;
  const initialLoading =
    selectedLoading &&
    !(resultMode === "measurements" ? measurements.data : bucketPage);

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <InsightsOutlined color="primary" fontSize="large" />
          <Box>
            <Typography variant="h4">Análises</Typography>
            <Typography color="text.secondary">
              Consulte medições detalhadas ou buckets numéricos agregados.
            </Typography>
          </Box>
        </Stack>
      </Box>

      <SharedObservationQuery
        rooms={rooms.data ?? []}
        parameters={parametros}
        loadingCatalogs={rooms.isLoading || sensorTypes.isLoading}
      />

      <AnalyticsFilters
        value={draftFilters}
        sensors={sensors.data ?? []}
        sensorTypes={[...new Set((sensorTypes.data ?? []).map((type) => type.nome))].sort((a, b) => a.localeCompare(b))}
        parametros={parametros}
        compartimentos={rooms.data ?? []}
        loadingCatalogs={
          sensors.isLoading || sensorTypes.isLoading || rooms.isLoading
        }
        onChange={(filters) => {
          setDraftFilters(filters);
          setValidationError("");
        }}
        onApply={applyFilters}
        onClearOptional={clearOptionalFilters}
      />

      <TextField
        select
        label="Resultado"
        value={resultMode}
        onChange={(event) =>
          setResultMode(event.target.value as "buckets" | "measurements")
        }
        sx={{ maxWidth: 320 }}
      >
        <MenuItem value="buckets">Buckets agregados</MenuItem>
        <MenuItem value="measurements">Medições detalhadas</MenuItem>
      </TextField>

      {validationError && <Alert severity="warning">{validationError}</Alert>}
      {catalogError && (
        <Alert severity="warning">
          Nao foi possivel carregar todos os filtros de catalogo:{" "}
          {analyticsErrorMessage(catalogError)}
        </Alert>
      )}
      {(resultMode === "measurements"
        ? measurements.isFetching
        : buckets.isFetching || summary.isFetching) &&
        !initialLoading && (
        <Box aria-live="polite">
          <LinearProgress />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Atualizando analise...
          </Typography>
        </Box>
      )}
      {initialLoading && (
        <Stack
          direction="row"
          spacing={1.5}
          alignItems="center"
          aria-live="polite"
        >
          <CircularProgress size={24} />
          <Typography>Carregando analise...</Typography>
        </Stack>
      )}
      {resultMode === "buckets" && buckets.isError && (
        <Alert severity="error">{analyticsErrorMessage(buckets.error)}</Alert>
      )}
      {resultMode === "buckets" && summary.isError && (
        <Alert severity="error">{analyticsErrorMessage(summary.error)}</Alert>
      )}
      {resultMode === "measurements" &&
        !appliedFilters.compartimentoId &&
        !appliedFilters.sensorExternalId && (
        <Alert severity="info">
          Selecione um compartimento ou sensor nos filtros e aplique para
          consultar as medições detalhadas.
        </Alert>
      )}
      {resultMode === "measurements" &&
        (appliedFilters.parametroDefId || appliedFilters.aggregationVersion) && (
          <Alert severity="info">
            Filtros de parâmetro e versão de agregação se aplicam aos buckets.
            No modo de medições detalhadas, os valores de todos os parâmetros
            vêm na própria medição.
          </Alert>
        )}
      {resultMode === "measurements" && measurements.isError && (
        <Alert severity="error">
          {analyticsErrorMessage(measurements.error)}
        </Alert>
      )}
      {resultMode === "measurements" &&
        (appliedFilters.compartimentoId || appliedFilters.sensorExternalId) && (
        <MeasurementResults
          measurements={measurements.data ?? []}
          loading={measurements.isFetching}
          page={appliedFilters.page ?? 0}
          pageSize={appliedFilters.size ?? 20}
          onPageChange={changePage}
        />
      )}

      {resultMode === "buckets" && !buckets.isError && (
        <>
          <AnalyticsSummaryCards
            summaries={summary.data ?? []}
            loading={summary.isFetching}
          />
          {hasNoData ? (
            <>
              <Alert severity="info">
                Buckets são materializados por um job de agregação assíncrono;
                a chegada de medições canônicas, sozinha, não cria buckets.
                Verifique se há jobs concluídos para este período e se o worker
                de agregação está habilitado.
              </Alert>
              <AnalyticsEmptyState message="Filtros existentes, mas sem buckets compativeis no periodo informado." />
            </>
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

function MeasurementResults({
  measurements,
  loading,
  page,
  pageSize,
  onPageChange,
}: {
  measurements: Medicao[];
  loading: boolean;
  page: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <Stack spacing={1}>
      {loading && <LinearProgress />}
      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Horário medido</TableCell>
              <TableCell>Sensor</TableCell>
              <TableCell>Tipo</TableCell>
              <TableCell>Origem</TableCell>
              <TableCell>Valores</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {measurements.map((measurement) => (
              <TableRow key={measurement.id} hover>
                <TableCell>
                  {new Date(measurement.timestamp).toLocaleString("pt-BR")}
                </TableCell>
                <TableCell>{measurement.sensorExternalId}</TableCell>
                <TableCell>{measurement.tipoNome}</TableCell>
                <TableCell>{measurement.source}</TableCell>
                <TableCell sx={{ whiteSpace: "pre-wrap" }}>
                  {Object.entries(measurement.valores)
                    .map(([name, value]) => `${name}: ${String(value)}`)
                    .join("\n")}
                </TableCell>
              </TableRow>
            ))}
            {!loading && measurements.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  Nenhuma medição encontrada no período.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
      <Stack direction="row" spacing={1} justifyContent="flex-end">
        <Button
          disabled={page === 0 || loading}
          onClick={() => onPageChange(page - 1)}
        >
          Anterior
        </Button>
        <Typography sx={{ alignSelf: "center" }}>Página {page + 1}</Typography>
        <Button
          disabled={measurements.length < pageSize || loading}
          onClick={() => onPageChange(page + 1)}
        >
          Próxima
        </Button>
      </Stack>
    </Stack>
  );
}
