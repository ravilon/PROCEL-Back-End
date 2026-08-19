import {
  ErrorOutlineOutlined,
  RefreshOutlined,
  SearchOutlined,
  VisibilityOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { getTelemetryEvent, listTelemetryEvents, reprocessTelemetryEvent } from "../api/telemetry";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../lib/api";
import type { RawTelemetryEvent, RawTelemetryStatus, TelemetrySource } from "../types/telemetry";

const statuses: RawTelemetryStatus[] = [
  "RECEIVED",
  "PROCESSING",
  "CANONICAL_ACCEPTED",
  "CANONICAL_DUPLICATE",
  "CANONICAL_CONFLICT",
  "CANONICAL_FAILED",
  "DISCARDED",
];

const sources: TelemetrySource[] = ["REST", "MQTT"];
const reprocessableStatuses = new Set<RawTelemetryStatus>([
  "CANONICAL_FAILED",
  "CANONICAL_CONFLICT",
  "DISCARDED",
]);

export function TelemetryOperationsPage() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [source, setSource] = useState<TelemetrySource | "">("");
  const [status, setStatus] = useState<RawTelemetryStatus | "">("");
  const [sensorId, setSensorId] = useState("");
  const [producerId, setProducerId] = useState("");
  const [messageId, setMessageId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reprocessTarget, setReprocessTarget] = useState<RawTelemetryEvent | null>(null);
  const [reason, setReason] = useState("");
  const reprocessInFlight = useRef(false);

  const filters = useMemo(() => ({
    source,
    status,
    sensorId,
    producerId,
    messageId,
    from: toInstantParam(from),
    to: toInstantParam(to),
    page,
    size: 20,
  }), [source, status, sensorId, producerId, messageId, from, to, page]);

  const events = useQuery({
    queryKey: ["telemetry", "events", filters],
    queryFn: () => listTelemetryEvents(filters, session),
  });

  const details = useQuery({
    queryKey: ["telemetry", "event", selectedId],
    queryFn: () => getTelemetryEvent(selectedId ?? "", session),
    enabled: Boolean(selectedId),
  });

  const reprocess = useMutation({
    mutationFn: () => {
      if (!reprocessTarget) throw new Error("Evento nao selecionado");
      return reprocessTelemetryEvent(reprocessTarget.id, reason, session);
    },
    onSuccess: async () => {
      setReason("");
      setReprocessTarget(null);
      await queryClient.invalidateQueries({ queryKey: ["telemetry", "events"] });
      await queryClient.invalidateQueries({ queryKey: ["telemetry", "event"] });
    },
    onSettled: () => {
      reprocessInFlight.current = false;
    },
  });

  const rows = events.data?.content ?? [];
  const totalPages = events.data?.totalPages ?? 0;
  const normalizedReason = reason.trim();
  const reasonError = normalizedReason.length > 500;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Telemetria</Typography>
        <Typography color="text.secondary">
          Operacao dos eventos brutos recebidos e encaminhados para processamento canonico.
        </Typography>
      </Box>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
          <FormControl sx={{ minWidth: 150 }}>
            <InputLabel id="telemetry-source-label">Source</InputLabel>
            <Select
              labelId="telemetry-source-label"
              label="Source"
              value={source}
              onChange={(event) => {
                setSource(event.target.value as TelemetrySource | "");
                setPage(0);
              }}
            >
              <MenuItem value="">Todos</MenuItem>
              {sources.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl sx={{ minWidth: 220 }}>
            <InputLabel id="telemetry-status-label">Status</InputLabel>
            <Select
              labelId="telemetry-status-label"
              label="Status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as RawTelemetryStatus | "");
                setPage(0);
              }}
            >
              <MenuItem value="">Todos</MenuItem>
              {statuses.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}
            </Select>
          </FormControl>
          <FilterText label="Sensor" value={sensorId} onChange={setSensorId} onResetPage={() => setPage(0)} />
          <FilterText label="Producer" value={producerId} onChange={setProducerId} onResetPage={() => setPage(0)} />
          <FilterText label="Message ID" value={messageId} onChange={setMessageId} onResetPage={() => setPage(0)} />
        </Stack>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ mt: 2 }}>
          <TextField
            label="Recebido de"
            type="datetime-local"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              setPage(0);
            }}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Recebido ate"
            type="datetime-local"
            value={to}
            onChange={(event) => {
              setTo(event.target.value);
              setPage(0);
            }}
            InputLabelProps={{ shrink: true }}
          />
          <Button startIcon={<RefreshOutlined />} onClick={() => events.refetch()} disabled={events.isFetching}>
            Atualizar
          </Button>
        </Stack>
      </Paper>

      {events.isLoading && <CircularProgress size={24} />}
      {events.isError && <Alert severity="error">{events.error.message}</Alert>}
      {!events.isLoading && !events.isError && (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Status</TableCell>
                  <TableCell>Recebido</TableCell>
                  <TableCell>Source</TableCell>
                  <TableCell>Producer</TableCell>
                  <TableCell>Sensor</TableCell>
                  <TableCell>Message ID</TableCell>
                  <TableCell align="right">Acoes</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((event) => (
                  <TableRow key={event.id} hover>
                    <TableCell><StatusChip status={event.status} /></TableCell>
                    <TableCell>{formatDate(event.receivedAt)}</TableCell>
                    <TableCell>{event.source}</TableCell>
                    <TableCell sx={{ overflowWrap: "anywhere" }}>{event.producerId}</TableCell>
                    <TableCell sx={{ overflowWrap: "anywhere" }}>{event.sensorId ?? "-"}</TableCell>
                    <TableCell sx={{ overflowWrap: "anywhere" }}>{event.messageId}</TableCell>
                    <TableCell align="right">
                      <Tooltip title="Detalhes">
                        <IconButton aria-label="Detalhes" onClick={() => setSelectedId(event.id)}>
                          <VisibilityOutlined />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Reprocessar">
                        <span>
                          <IconButton
                            aria-label="Reprocessar"
                            disabled={!reprocessableStatuses.has(event.status)}
                            onClick={() => {
                              setReason("");
                              setReprocessTarget(event);
                            }}
                          >
                            <RefreshOutlined />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center">Nenhum evento encontrado.</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={2} sx={{ p: 2 }}>
            <Typography color="text.secondary">
              Pagina {page + 1} de {Math.max(totalPages, 1)}
            </Typography>
            <Button disabled={page === 0 || events.isFetching} onClick={() => setPage((value) => Math.max(0, value - 1))}>
              Anterior
            </Button>
            <Button
              disabled={events.isFetching || page + 1 >= totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Proxima
            </Button>
          </Stack>
        </Paper>
      )}

      <Drawer anchor="right" open={Boolean(selectedId)} onClose={() => setSelectedId(null)}>
        <Box sx={{ width: { xs: "100vw", sm: 560 }, p: 3 }}>
          {details.isLoading && <CircularProgress size={24} />}
          {details.isError && <Alert severity="error">{details.error.message}</Alert>}
          {details.data && <TelemetryDetails event={details.data} onReprocess={setReprocessTarget} />}
        </Box>
      </Drawer>

      <Dialog
        open={Boolean(reprocessTarget)}
        onClose={() => !reprocess.isPending && setReprocessTarget(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Reprocessar evento</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning" icon={<ErrorOutlineOutlined />}>
              O evento bruto nao sera alterado. Ele voltara para RECEIVED e sera processado pelo worker.
            </Alert>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>
              {reprocessTarget?.id}
            </Typography>
            <TextField
              label="Motivo"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              required
              multiline
              minRows={3}
              error={reasonError}
              helperText={`${normalizedReason.length}/500`}
            />
            {reprocess.error && (
              <Alert severity="error">
                {reprocess.error instanceof ApiError && reprocess.error.code
                  ? `${reprocess.error.code}: ${reprocess.error.message}`
                  : reprocess.error.message}
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReprocessTarget(null)} disabled={reprocess.isPending}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => {
              if (reprocessInFlight.current) return;
              reprocessInFlight.current = true;
              reprocess.mutate();
            }}
            disabled={reprocess.isPending || normalizedReason.length < 1 || reasonError}
          >
            Confirmar reprocessamento
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function FilterText({
  label,
  value,
  onChange,
  onResetPage,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onResetPage: () => void;
}) {
  return (
    <TextField
      label={label}
      value={value}
      onChange={(event) => {
        onChange(event.target.value);
        onResetPage();
      }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchOutlined />
          </InputAdornment>
        ),
      }}
    />
  );
}

function TelemetryDetails({ event, onReprocess }: {
  event: RawTelemetryEvent;
  onReprocess: (event: RawTelemetryEvent) => void;
}) {
  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" gap={2}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h6" sx={{ overflowWrap: "anywhere" }}>{event.messageId}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>
            {event.id}
          </Typography>
        </Box>
        <StatusChip status={event.status} />
      </Stack>
      <Divider />
      <Detail label="Producer" value={event.producerId} />
      <Detail label="Sensor" value={event.sensorId ?? "-"} />
      <Detail label="Source" value={event.source} />
      <Detail label="Recebido" value={formatDate(event.receivedAt)} />
      <Detail label="Timestamp origem" value={formatDate(event.sourceTimestamp)} />
      <Detail label="Payload hash" value={event.payloadHash} />
      <Detail label="Tentativas" value={String(event.processing?.attempts ?? 0)} />
      <Detail label="Ultimo erro" value={event.processing?.lastError ?? "-"} />
      <Detail label="Medicao canonica" value={event.processing?.canonicalMeasurementId ?? "-"} />
      <Detail label="Profile" value={event.processing?.profileId ?? "-"} />
      <Detail label="Parser" value={event.processing?.parserVersionId ?? "-"} />
      <Box>
        <Typography variant="subtitle2">Payload bruto</Typography>
        <Box
          component="pre"
          aria-label="Payload bruto readonly"
          sx={{
            bgcolor: "grey.100",
            border: 1,
            borderColor: "divider",
            borderRadius: 1,
            maxHeight: 320,
            overflow: "auto",
            p: 1.5,
            whiteSpace: "pre-wrap",
            overflowWrap: "anywhere",
            fontSize: 13,
          }}
        >
          {safeJson(event.payload)}
        </Box>
      </Box>
      <Box>
        <Typography variant="subtitle2">Historico de reprocessamento</Typography>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {(event.reprocessAudit ?? []).map((entry) => (
            <Paper key={`${entry.requestedAt}-${entry.requestedBy}`} variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="body2">{formatDate(entry.requestedAt)} por {entry.requestedBy}</Typography>
              <Typography variant="caption" color="text.secondary">
                {entry.previousStatus} | attempts {entry.attempts} | {entry.lastError ?? "sem erro"}
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.5, overflowWrap: "anywhere" }}>{entry.reason}</Typography>
            </Paper>
          ))}
          {(event.reprocessAudit ?? []).length === 0 && (
            <Typography variant="body2" color="text.secondary">Sem reprocessamentos.</Typography>
          )}
        </Stack>
      </Box>
      <Button
        startIcon={<RefreshOutlined />}
        variant="contained"
        disabled={!reprocessableStatuses.has(event.status)}
        onClick={() => onReprocess(event)}
      >
        Reprocessar
      </Button>
    </Stack>
  );
}

function StatusChip({ status }: { status: RawTelemetryStatus }) {
  const color = status === "CANONICAL_ACCEPTED" || status === "CANONICAL_DUPLICATE"
    ? "success"
    : status === "CANONICAL_FAILED" || status === "CANONICAL_CONFLICT" || status === "DISCARDED"
      ? "error"
      : status === "PROCESSING"
        ? "primary"
        : "default";
  return <Chip label={status} color={color} size="small" />;
}

function Detail({ label, value }: { label: string; value?: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>{value || "-"}</Typography>
    </Box>
  );
}

function formatDate(value?: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function toInstantParam(value: string) {
  if (!value) return "";
  return new Date(value).toISOString();
}

function safeJson(value: unknown) {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return "[payload indisponivel]";
  }
}
