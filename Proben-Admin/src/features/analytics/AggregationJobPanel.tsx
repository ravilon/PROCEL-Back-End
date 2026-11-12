import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import { createAggregationJob, getAggregationJob } from "../../api/analytics";
import { useAuth } from "../../auth/AuthContext";
import type { NumericBucketFilters } from "../../types";
import { analyticsErrorMessage } from "./analyticsUtils";

function toLocalDateTime(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function AggregationJobPanel({
  filters,
}: {
  filters: NumericBucketFilters;
}) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const canCreate = Boolean(
    session?.roles.some((role) => role === "ADMIN" || role === "OPERADOR"),
  );
  const [from, setFrom] = useState(() => toLocalDateTime(filters.from));
  const [to, setTo] = useState(() => toLocalDateTime(filters.to));
  const [windowDuration, setWindowDuration] = useState("PT1M");
  const [jobId, setJobId] = useState<string | null>(null);

  useEffect(() => {
    setFrom(toLocalDateTime(filters.from));
    setTo(toLocalDateTime(filters.to));
  }, [filters.from, filters.to]);

  const job = useQuery({
    queryKey: ["analytics", "aggregation-job", jobId],
    queryFn: () => getAggregationJob(jobId!, session),
    enabled: Boolean(jobId && session),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "COMPLETED" || status === "FAILED" ? false : 5000;
    },
  });

  const create = useMutation({
    mutationFn: () => {
      const start = new Date(from);
      const end = new Date(to);
      if (
        !from ||
        !to ||
        Number.isNaN(start.getTime()) ||
        Number.isNaN(end.getTime())
      ) {
        throw new Error("Informe um período válido para agregar.");
      }
      if (start >= end) throw new Error("O início deve ser anterior ao fim.");
      return createAggregationJob(
        {
          from: start.toISOString(),
          to: end.toISOString(),
          windowDuration,
          ...(filters.sensorExternalId
            ? { sensorExternalId: filters.sensorExternalId }
            : {}),
          ...(filters.compartimentoId
            ? { compartimentoId: filters.compartimentoId }
            : {}),
        },
        session,
      );
    },
    onSuccess: async (created) => {
      setJobId(created.id);
      await queryClient.invalidateQueries({
        queryKey: ["analytics", "aggregation-job"],
      });
    },
  });

  const currentJob = job.data;
  const terminal =
    currentJob?.status === "COMPLETED" || currentJob?.status === "FAILED";
  useEffect(() => {
    if (currentJob?.status === "COMPLETED") {
      void queryClient.invalidateQueries({
        queryKey: ["analytics", "numeric-buckets"],
      });
    }
  }, [currentJob?.status, queryClient]);

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="h6">Gerar buckets</Typography>
          <Typography variant="body2" color="text.secondary">
            Solicite a agregação das medições canônicas deste período. O worker
            da API precisa estar ativo.
          </Typography>
        </Box>
        {!canCreate && (
          <Alert severity="info">
            Seu perfil pode consultar buckets, mas a criação de jobs exige o
            perfil ADMIN ou OPERADOR.
          </Alert>
        )}
        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          <TextField
            label="De"
            type="datetime-local"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
          <TextField
            label="Até"
            type="datetime-local"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
          <TextField
            select
            label="Intervalo de cada bucket"
            value={windowDuration}
            onChange={(event) => setWindowDuration(event.target.value)}
            sx={{ minWidth: { md: 230 } }}
          >
            <MenuItem value="PT1M">1 minuto</MenuItem>
            <MenuItem value="PT5M">5 minutos</MenuItem>
            <MenuItem value="PT15M">15 minutos</MenuItem>
            <MenuItem value="PT1H">1 hora</MenuItem>
            <MenuItem value="PT6H">6 horas</MenuItem>
            <MenuItem value="P1D">1 dia</MenuItem>
          </TextField>
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Button
            variant="contained"
            disabled={
              !canCreate ||
              create.isPending ||
              (currentJob != null && !terminal)
            }
            onClick={() => create.mutate()}
          >
            {create.isPending ? "Solicitando..." : "Gerar buckets"}
          </Button>
          {currentJob && terminal && (
            <Button variant="text" onClick={() => setJobId(null)}>
              Nova agregação
            </Button>
          )}
        </Stack>
        <Typography variant="caption" color="text.secondary">
          O job agrega todas as medidas do período; os filtros de parâmetro e
          modelo restringem a consulta dos buckets, mas não a geração. Se houver
          sensor ou sala selecionados nos filtros aplicados, o job será limitado
          a esse escopo.
        </Typography>
        {create.isError && (
          <Alert severity="error">{analyticsErrorMessage(create.error)}</Alert>
        )}
        {job.isError && (
          <Alert severity="error">{analyticsErrorMessage(job.error)}</Alert>
        )}
        {currentJob && (
          <Box>
            <Alert
              severity={
                currentJob.status === "FAILED"
                  ? "error"
                  : currentJob.status === "COMPLETED"
                    ? "success"
                    : "info"
              }
            >
              Job {currentJob.id}: {currentJob.status}. Janelas concluídas:{" "}
              {currentJob.progress.completedWindows}/
              {currentJob.progress.totalWindows}; falhas:{" "}
              {currentJob.progress.failedWindows}.
              {currentJob.error ? ` Erro: ${currentJob.error}` : ""}
            </Alert>
            {!terminal && (
              <>
                <LinearProgress sx={{ mt: 1 }} />
                <Typography variant="caption" color="text.secondary">
                  Se permanecer pendente, confirme que o worker de agregação foi
                  habilitado no serviço da API.
                </Typography>
              </>
            )}
          </Box>
        )}
      </Stack>
    </Paper>
  );
}
