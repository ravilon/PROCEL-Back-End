import {
  AccountTreeOutlined,
  AddOutlined,
  EditOutlined,
  PlayArrowOutlined,
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
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useId, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  getMissionEvent,
  getMissionOccurrence,
  getMissionWindow,
  getMissionWorkerStatus,
  listEvaluationRequests,
  listMissionEvents,
  listMissionOccurrences,
  listMissionWindows,
  listOccurrenceEvidence,
  listWindowEvidence,
  operateMissionWindow,
  runMissionWorker,
  updateOccurrenceStatus,
} from "../api/missions";
import { useAuth } from "../auth/AuthContext";
import { ApiError, apiRequest } from "../lib/api";
import { AccessDeniedPage } from "./AccessDeniedPage";
import type {
  EventoAvaliacaoRequestStatus,
  EventoDefinicao,
  EventoJanelaAvaliacaoStatus,
  EventoModoAvaliacao,
  EventoOcorrenciaStatus,
  EventoTipoDisparo,
  MissionOccurrence,
  MissionWindow,
  Missao,
  OccurrenceEvidence,
  PageResponse,
  WindowEvidence,
} from "../types";
import { MissionTriggerConfigurator } from "../features/missions/MissionTriggerConfigurator";

const eventTypes: EventoTipoDisparo[] = ["MEDICAO_RECEBIDA", "CHECKIN_CONFIRMADO"];
const eventModes: EventoModoAvaliacao[] = ["INSTANTANEO", "DURACAO", "TRANSICAO", "JANELA_ENCERRADA"];
const requestStatuses: EventoAvaliacaoRequestStatus[] = ["PENDING", "PROCESSING", "COMPLETED", "FAILED", "RETRY"];
const windowStatuses: EventoJanelaAvaliacaoStatus[] = ["ABERTA", "PROCESSING", "SATISFEITA", "INVALIDADA", "EXPIRADA", "FAILED"];
const occurrenceStatuses: EventoOcorrenciaStatus[] = ["DETECTADO", "CONFIRMADO", "DESCARTADO", "PROCESSADO"];
const pageSize = 20;

type MissionTab = "catalog" | "events" | "requests" | "windows" | "occurrences" | "workers";
type WindowOperation = "retry" | "satisfy" | "invalidate" | "expire" | "fail";

export function MissionsAdminPage() {
  const { hasAnyRole } = useAuth();
  const [tab, setTab] = useState<MissionTab>("catalog");
  const canConsult = hasAnyRole("ADMIN", "OPERADOR", "ANALISTA");
  const canOperate = hasAnyRole("ADMIN");

  if (!canConsult) {
    return <AccessDeniedPage />;
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Missoes</Typography>
        <Typography color="text.secondary">
          Cadastro e operacao administrativa do motor persistente de eventos.
        </Typography>
      </Box>

      <Paper variant="outlined">
        <Tabs
          value={tab}
          onChange={(_, value: MissionTab) => setTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab value="catalog" label="Catalogo" />
          <Tab value="events" label="Eventos e condicoes" />
          <Tab value="requests" label="Requests" />
          <Tab value="windows" label="Janelas" />
          <Tab value="occurrences" label="Ocorrencias" />
          <Tab value="workers" label="Workers" />
        </Tabs>
      </Paper>

      {tab === "catalog" && <MissionCatalogPanel />}
      {tab === "events" && <MissionEventsPanel />}
      {tab === "requests" && <EvaluationRequestsPanel />}
      {tab === "windows" && <MissionWindowsPanel canOperate={canOperate} />}
      {tab === "occurrences" && <MissionOccurrencesPanel canOperate={canOperate} />}
      {tab === "workers" && <MissionWorkersPanel canOperate={canOperate} />}
    </Stack>
  );
}

function MissionCatalogPanel() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const [showOnlyActive, setShowOnlyActive] = useState(false);
  const [form, setForm] = useState({
    titulo: "",
    descricao: "",
    tipo: "Individual",
    value: "0",
    ativo: true,
    parentId: "",
  });
  const missions = useQuery({
    queryKey: ["missions", { showOnlyActive }],
    queryFn: () => apiRequest<Missao[]>(showOnlyActive ? "/api/missoes?ativo=true" : "/api/missoes", {}, session),
  });
  const roots = useMemo(
    () => missions.data?.filter((mission) => !mission.parentId) ?? [],
    [missions.data],
  );
  const createMission = useMutation({
    mutationFn: () =>
      apiRequest<Missao>(
        "/api/missoes",
        {
          method: "POST",
          body: JSON.stringify({
            ...form,
            value: Number(form.value),
            parentId: form.parentId || null,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setForm({
        titulo: "",
        descricao: "",
        tipo: "Individual",
        value: "0",
        ativo: true,
        parentId: "",
      });
      await queryClient.invalidateQueries({ queryKey: ["missions"] });
    },
  });

  return (
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "380px 1fr" }, gap: 2 }}>
      <Paper
        component="form"
        variant="outlined"
        sx={{ p: 2, alignSelf: "start" }}
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          createMission.mutate();
        }}
      >
        <Typography variant="h6">Nova missao</Typography>
        <Stack spacing={2} sx={{ mt: 2 }}>
          <TextField label="Titulo" value={form.titulo} onChange={(event) => setForm({ ...form, titulo: event.target.value })} required />
          <TextField label="Descricao" value={form.descricao} onChange={(event) => setForm({ ...form, descricao: event.target.value })} multiline minRows={3} />
          <FormControl>
            <InputLabel>Missao pai</InputLabel>
            <Select label="Missao pai" value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}>
              <MenuItem value="">Nenhuma, missao raiz</MenuItem>
              {missions.data?.map((mission) => <MenuItem key={mission.id} value={mission.id}>{mission.titulo}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="Tipo" value={form.tipo} onChange={(event) => setForm({ ...form, tipo: event.target.value })} />
          <TextField label="XP" type="number" value={form.value} onChange={(event) => setForm({ ...form, value: event.target.value })} inputProps={{ min: 0 }} />
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Typography>Disponivel para atribuicao</Typography>
            <Switch checked={form.ativo} onChange={(event) => setForm({ ...form, ativo: event.target.checked })} />
          </Stack>
          <Button type="submit" variant="contained" startIcon={<AddOutlined />} disabled={createMission.isPending}>
            Criar missao
          </Button>
          {createMission.error && <Alert severity="error">{formatError(createMission.error)}</Alert>}
        </Stack>
      </Paper>

      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <AccountTreeOutlined />
          <Typography variant="h6">Arvore de missoes</Typography>
          <Box sx={{ flexGrow: 1 }} />
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2" color="text.secondary">Somente ativas</Typography>
            <Switch
              checked={showOnlyActive}
              onChange={(event) => setShowOnlyActive(event.target.checked)}
              inputProps={{ "aria-label": "Mostrar somente missoes ativas" }}
            />
          </Stack>
        </Stack>
        {missions.isLoading && <CircularProgress size={24} />}
        {roots.map((mission) => (
          <MissionNode
            key={mission.id}
            mission={mission}
            missions={missions.data ?? []}
            onUpdated={() => queryClient.invalidateQueries({ queryKey: ["missions"] })}
            canConfigure={hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")}
          />
        ))}
        {!missions.isLoading && roots.length === 0 && (
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography color="text.secondary">Nenhuma missao cadastrada.</Typography>
          </Paper>
        )}
        {missions.error && <Alert severity="error">{formatError(missions.error)}</Alert>}
      </Stack>
    </Box>
  );
}

function MissionEventsPanel() {
  const { session } = useAuth();
  const [missionId, setMissionId] = useState("");
  const [active, setActive] = useState<boolean | "">("");
  const [tipoDisparo, setTipoDisparo] = useState<EventoTipoDisparo | "">("");
  const [modoAvaliacao, setModoAvaliacao] = useState<EventoModoAvaliacao | "">("");
  const [page, setPage] = useState(0);
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const filters = useMemo(() => ({
    missionId: missionId.trim(),
    active,
    tipoDisparo,
    modoAvaliacao,
    page,
    size: pageSize,
  }), [active, missionId, modoAvaliacao, page, tipoDisparo]);
  const events = useQuery({
    queryKey: ["missions", "admin-events", filters],
    queryFn: () => listMissionEvents(filters, session),
  });
  const details = useQuery({
    queryKey: ["missions", "event-detail", selectedEventId],
    queryFn: () => getMissionEvent(selectedEventId ?? "", session),
    enabled: Boolean(selectedEventId),
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
          <FilterText label="Missao ID" value={missionId} onChange={setMissionId} onResetPage={() => setPage(0)} />
          <FormControl sx={{ minWidth: 160 }}>
            <InputLabel>Ativo</InputLabel>
            <Select label="Ativo" value={String(active)} onChange={(event) => {
              setActive(event.target.value === "" ? "" : event.target.value === "true");
              setPage(0);
            }}>
              <MenuItem value="">Todos</MenuItem>
              <MenuItem value="true">Ativos</MenuItem>
              <MenuItem value="false">Inativos</MenuItem>
            </Select>
          </FormControl>
          <EnumSelect label="Disparo" value={tipoDisparo} options={eventTypes} onChange={(value) => {
            setTipoDisparo(value as EventoTipoDisparo | "");
            setPage(0);
          }} />
          <EnumSelect label="Modo" value={modoAvaliacao} options={eventModes} onChange={(value) => {
            setModoAvaliacao(value as EventoModoAvaliacao | "");
            setPage(0);
          }} />
          <Button startIcon={<RefreshOutlined />} onClick={() => events.refetch()} disabled={events.isFetching}>
            Atualizar
          </Button>
        </Stack>
      </Paper>
      <PagedTable
        query={events}
        page={page}
        setPage={setPage}
        emptyText="Nenhum evento encontrado."
        headers={["Evento", "Missao", "Disparo", "Modo", "Ativo", "Ordem", "Acoes"]}
        renderRow={(event) => (
          <TableRow key={event.id} hover>
            <TableCell sx={{ overflowWrap: "anywhere" }}>
              <Typography fontWeight={700}>{event.nome}</Typography>
              <Typography variant="caption" color="text.secondary">{event.id}</Typography>
            </TableCell>
            <TableCell>{event.missaoTitulo}</TableCell>
            <TableCell>{event.tipoDisparo}</TableCell>
            <TableCell>{event.modoAvaliacao}</TableCell>
            <TableCell><ActiveChip active={event.ativo} /></TableCell>
            <TableCell>{event.ordem ?? "-"}</TableCell>
            <TableCell align="right">
              <Tooltip title="Condicoes">
                <IconButton aria-label={`Ver condicoes de ${event.nome}`} onClick={() => setSelectedEventId(event.id)}>
                  <VisibilityOutlined />
                </IconButton>
              </Tooltip>
            </TableCell>
          </TableRow>
        )}
      />
      <Drawer anchor="right" open={Boolean(selectedEventId)} onClose={() => setSelectedEventId(null)}>
        <DetailsPane loading={details.isLoading} error={details.error}>
          {details.data && <EventDetails event={details.data} />}
        </DetailsPane>
      </Drawer>
    </Stack>
  );
}

function EvaluationRequestsPanel() {
  const { session } = useAuth();
  const [status, setStatus] = useState<EventoAvaliacaoRequestStatus | "">("");
  const [medicaoId, setMedicaoId] = useState("");
  const [page, setPage] = useState(0);
  const filters = useMemo(() => ({ status, medicaoId: medicaoId.trim(), page, size: pageSize }), [medicaoId, page, status]);
  const requests = useQuery({
    queryKey: ["missions", "evaluation-requests", filters],
    queryFn: () => listEvaluationRequests(filters, session),
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
          <EnumSelect label="Status" value={status} options={requestStatuses} onChange={(value) => {
            setStatus(value as EventoAvaliacaoRequestStatus | "");
            setPage(0);
          }} />
          <FilterText label="Medicao ID" value={medicaoId} onChange={setMedicaoId} onResetPage={() => setPage(0)} />
          <Button startIcon={<RefreshOutlined />} onClick={() => requests.refetch()} disabled={requests.isFetching}>
            Atualizar
          </Button>
        </Stack>
      </Paper>
      <PagedTable
        query={requests}
        page={page}
        setPage={setPage}
        emptyText="Nenhum request encontrado."
        headers={["Status", "Medicao", "Tentativas", "Disponivel", "Lease", "Processado", "Erro"]}
        renderRow={(request) => (
          <TableRow key={request.id} hover>
            <TableCell><StatusChip status={request.status} /></TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{request.medicaoId}</TableCell>
            <TableCell>{request.attempts}</TableCell>
            <TableCell>{formatDate(request.availableAt)}</TableCell>
            <TableCell>{formatDate(request.leaseUntil)}</TableCell>
            <TableCell>{formatDate(request.processedAt)}</TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{request.lastError ?? "-"}</TableCell>
          </TableRow>
        )}
      />
    </Stack>
  );
}

function MissionWindowsPanel({ canOperate }: { canOperate: boolean }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<EventoJanelaAvaliacaoStatus | "">("");
  const [eventoDefinicaoId, setEventoDefinicaoId] = useState("");
  const [compartimentoId, setCompartimentoId] = useState("");
  const [periodoAulaId, setPeriodoAulaId] = useState("");
  const [page, setPage] = useState(0);
  const [selectedWindowId, setSelectedWindowId] = useState<string | null>(null);
  const [operation, setOperation] = useState<{ window: MissionWindow; type: WindowOperation } | null>(null);
  const filters = useMemo(() => ({
    status,
    eventoDefinicaoId: eventoDefinicaoId.trim(),
    compartimentoId: compartimentoId.trim(),
    periodoAulaId: periodoAulaId.trim(),
    page,
    size: pageSize,
  }), [compartimentoId, eventoDefinicaoId, page, periodoAulaId, status]);
  const windows = useQuery({
    queryKey: ["missions", "windows", filters],
    queryFn: () => listMissionWindows(filters, session),
  });
  const details = useQuery({
    queryKey: ["missions", "window", selectedWindowId],
    queryFn: () => getMissionWindow(selectedWindowId ?? "", session),
    enabled: Boolean(selectedWindowId),
  });
  const evidences = useQuery({
    queryKey: ["missions", "window-evidences", selectedWindowId],
    queryFn: () => listWindowEvidence(selectedWindowId ?? "", 0, 50, session),
    enabled: Boolean(selectedWindowId),
  });
  const operate = useMutation({
    mutationFn: ({ type, reason }: { type: WindowOperation; reason: string }) => {
      if (!operation) throw new Error("Janela nao selecionada");
      return operateMissionWindow(operation.window.id, type, { reason: reason.trim() || undefined }, session);
    },
    onSuccess: async () => {
      setOperation(null);
      await queryClient.invalidateQueries({ queryKey: ["missions", "windows"] });
      await queryClient.invalidateQueries({ queryKey: ["missions", "window"] });
    },
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", xl: "row" }} spacing={2}>
          <EnumSelect label="Status" value={status} options={windowStatuses} onChange={(value) => {
            setStatus(value as EventoJanelaAvaliacaoStatus | "");
            setPage(0);
          }} />
          <FilterText label="Evento ID" value={eventoDefinicaoId} onChange={setEventoDefinicaoId} onResetPage={() => setPage(0)} />
          <FilterText label="Compartimento" value={compartimentoId} onChange={setCompartimentoId} onResetPage={() => setPage(0)} />
          <FilterText label="Periodo aula ID" value={periodoAulaId} onChange={setPeriodoAulaId} onResetPage={() => setPage(0)} />
          <Button startIcon={<RefreshOutlined />} onClick={() => windows.refetch()} disabled={windows.isFetching}>
            Atualizar
          </Button>
        </Stack>
      </Paper>
      <PagedTable
        query={windows}
        page={page}
        setPage={setPage}
        emptyText="Nenhuma janela encontrada."
        headers={["Status", "Evento", "Compartimento", "Inicio", "Fim previsto", "Lease", "Tentativas", "Acoes"]}
        renderRow={(window) => (
          <TableRow key={window.id} hover>
            <TableCell><StatusChip status={window.status} /></TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{window.eventoNome}</TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{window.compartimentoId}</TableCell>
            <TableCell>{formatDate(window.inicioEm)}</TableCell>
            <TableCell>{formatDate(window.fimPrevistoEm)}</TableCell>
            <TableCell>{formatDate(window.leaseUntil)}</TableCell>
            <TableCell>{window.attempts}</TableCell>
            <TableCell align="right">
              <Tooltip title="Detalhes e evidencias">
                <IconButton aria-label={`Ver evidencias da janela ${window.id}`} onClick={() => setSelectedWindowId(window.id)}>
                  <VisibilityOutlined />
                </IconButton>
              </Tooltip>
              {canOperate && (
                <WindowOperationButtons window={window} onOperate={(type) => setOperation({ window, type })} />
              )}
            </TableCell>
          </TableRow>
        )}
      />
      <Drawer anchor="right" open={Boolean(selectedWindowId)} onClose={() => setSelectedWindowId(null)}>
        <DetailsPane loading={details.isLoading || evidences.isLoading} error={details.error ?? evidences.error}>
          {details.data && <WindowDetails window={details.data} evidences={evidences.data?.content ?? []} />}
        </DetailsPane>
      </Drawer>
      <WindowOperationDialog operation={operation} mutate={operate.mutate} pending={operate.isPending} error={operate.error} onClose={() => setOperation(null)} />
    </Stack>
  );
}

function MissionOccurrencesPanel({ canOperate }: { canOperate: boolean }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<EventoOcorrenciaStatus | "">("");
  const [eventoDefinicaoId, setEventoDefinicaoId] = useState("");
  const [compartimentoId, setCompartimentoId] = useState("");
  const [periodoAulaId, setPeriodoAulaId] = useState("");
  const [page, setPage] = useState(0);
  const [selectedOccurrenceId, setSelectedOccurrenceId] = useState<string | null>(null);
  const [statusTarget, setStatusTarget] = useState<MissionOccurrence | null>(null);
  const [nextStatus, setNextStatus] = useState<EventoOcorrenciaStatus>("CONFIRMADO");
  const filters = useMemo(() => ({
    status,
    eventoDefinicaoId: eventoDefinicaoId.trim(),
    compartimentoId: compartimentoId.trim(),
    periodoAulaId: periodoAulaId.trim(),
    page,
    size: pageSize,
  }), [compartimentoId, eventoDefinicaoId, page, periodoAulaId, status]);
  const occurrences = useQuery({
    queryKey: ["missions", "occurrences", filters],
    queryFn: () => listMissionOccurrences(filters, session),
  });
  const details = useQuery({
    queryKey: ["missions", "occurrence", selectedOccurrenceId],
    queryFn: () => getMissionOccurrence(selectedOccurrenceId ?? "", session),
    enabled: Boolean(selectedOccurrenceId),
  });
  const evidences = useQuery({
    queryKey: ["missions", "occurrence-evidences", selectedOccurrenceId],
    queryFn: () => listOccurrenceEvidence(selectedOccurrenceId ?? "", 0, 50, session),
    enabled: Boolean(selectedOccurrenceId),
  });
  const updateStatus = useMutation({
    mutationFn: () => {
      if (!statusTarget) throw new Error("Ocorrencia nao selecionada");
      return updateOccurrenceStatus(statusTarget.id, nextStatus, session);
    },
    onSuccess: async () => {
      setStatusTarget(null);
      await queryClient.invalidateQueries({ queryKey: ["missions", "occurrences"] });
      await queryClient.invalidateQueries({ queryKey: ["missions", "occurrence"] });
    },
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", xl: "row" }} spacing={2}>
          <EnumSelect label="Status" value={status} options={occurrenceStatuses} onChange={(value) => {
            setStatus(value as EventoOcorrenciaStatus | "");
            setPage(0);
          }} />
          <FilterText label="Evento ID" value={eventoDefinicaoId} onChange={setEventoDefinicaoId} onResetPage={() => setPage(0)} />
          <FilterText label="Compartimento" value={compartimentoId} onChange={setCompartimentoId} onResetPage={() => setPage(0)} />
          <FilterText label="Periodo aula ID" value={periodoAulaId} onChange={setPeriodoAulaId} onResetPage={() => setPage(0)} />
          <Button startIcon={<RefreshOutlined />} onClick={() => occurrences.refetch()} disabled={occurrences.isFetching}>
            Atualizar
          </Button>
        </Stack>
      </Paper>
      <PagedTable
        query={occurrences}
        page={page}
        setPage={setPage}
        emptyText="Nenhuma ocorrencia encontrada."
        headers={["Status", "Evento", "Compartimento", "Sensor", "Detectado", "Fingerprint", "Acoes"]}
        renderRow={(occurrence) => (
          <TableRow key={occurrence.id} hover>
            <TableCell><StatusChip status={occurrence.status} /></TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{occurrence.eventoNome}</TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{occurrence.compartimentoId ?? "-"}</TableCell>
            <TableCell sx={{ overflowWrap: "anywhere" }}>{occurrence.sensorExternalId ?? "-"}</TableCell>
            <TableCell>{formatDate(occurrence.detectadoEm)}</TableCell>
            <TableCell sx={{ maxWidth: 180, overflowWrap: "anywhere" }}>{occurrence.conteudoFingerprint ?? "-"}</TableCell>
            <TableCell align="right">
              <Tooltip title="Detalhes e evidencias">
                <IconButton aria-label={`Ver evidencias da ocorrencia ${occurrence.id}`} onClick={() => setSelectedOccurrenceId(occurrence.id)}>
                  <VisibilityOutlined />
                </IconButton>
              </Tooltip>
              {canOperate && (
                <Tooltip title="Alterar status">
                  <IconButton aria-label={`Alterar status da ocorrencia ${occurrence.id}`} onClick={() => {
                    setStatusTarget(occurrence);
                    setNextStatus(occurrence.status === "DETECTADO" ? "CONFIRMADO" : occurrence.status);
                  }}>
                    <EditOutlined />
                  </IconButton>
                </Tooltip>
              )}
            </TableCell>
          </TableRow>
        )}
      />
      <Drawer anchor="right" open={Boolean(selectedOccurrenceId)} onClose={() => setSelectedOccurrenceId(null)}>
        <DetailsPane loading={details.isLoading || evidences.isLoading} error={details.error ?? evidences.error}>
          {details.data && <OccurrenceDetails occurrence={details.data} evidences={evidences.data?.content ?? []} />}
        </DetailsPane>
      </Drawer>
      <Dialog open={Boolean(statusTarget)} onClose={() => !updateStatus.isPending && setStatusTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Alterar status da ocorrencia</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>{statusTarget?.id}</Typography>
            <EnumSelect label="Novo status" value={nextStatus} options={occurrenceStatuses} onChange={(value) => setNextStatus(value as EventoOcorrenciaStatus)} />
            {updateStatus.error && <Alert severity="error">{formatError(updateStatus.error)}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStatusTarget(null)} disabled={updateStatus.isPending}>Cancelar</Button>
          <Button variant="contained" onClick={() => updateStatus.mutate()} disabled={updateStatus.isPending}>
            Salvar status
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function MissionWorkersPanel({ canOperate }: { canOperate: boolean }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [runningWorker, setRunningWorker] = useState<"evaluation" | "temporal-windows" | null>(null);
  const status = useQuery({
    queryKey: ["missions", "worker-status"],
    queryFn: () => getMissionWorkerStatus(session),
  });
  const run = useMutation({
    mutationFn: (worker: "evaluation" | "temporal-windows") => runMissionWorker(worker, session),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["missions", "worker-status"] });
    },
    onSettled: () => {
      setRunningWorker(null);
    },
  });

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1}>
        <Button startIcon={<RefreshOutlined />} onClick={() => status.refetch()} disabled={status.isFetching}>
          Atualizar status
        </Button>
      </Stack>
      {status.isLoading && <CircularProgress size={24} />}
      {status.error && <Alert severity="error">{formatError(status.error)}</Alert>}
      {status.data && (
        <>
          <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "1fr 1fr" }, gap: 2 }}>
            <WorkerCard
              title="Worker instantaneo"
              state={status.data.evaluation}
              canOperate={canOperate}
              pending={run.isPending && runningWorker === "evaluation"}
              onRun={() => {
                if (runningWorker) return;
                setRunningWorker("evaluation");
                run.mutate("evaluation");
              }}
            />
            <WorkerCard
              title="Worker temporal"
              state={status.data.temporalWindows}
              canOperate={canOperate}
              pending={run.isPending && runningWorker === "temporal-windows"}
              onRun={() => {
                if (runningWorker) return;
                setRunningWorker("temporal-windows");
                run.mutate("temporal-windows");
              }}
            />
          </Box>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="h6">Drools e limites temporais</Typography>
            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" }, gap: 2, mt: 2 }}>
              <Detail label="Engine selecionado" value={status.data.drools.selectedRuleEngine} />
              <Detail label="Drools temporal" value={status.data.drools.temporalDroolsEnabled ? "habilitado" : "desabilitado"} />
              <Detail label="Atividades temporais" value={status.data.drools.temporalActivitiesEnabled ? "habilitado" : "desabilitado"} />
              <Detail label="Max fatos" value={String(status.data.drools.maxFactsPerEvaluation)} />
              <Detail label="Cache max" value={String(status.data.drools.maxCacheEntries)} />
              <Detail label="Lacuna maxima" value={status.data.drools.maximumSampleGap} />
              <Detail label="Expiracao cache" value={status.data.drools.cacheExpiration} />
              <Detail label="Timeout avaliacao" value={status.data.drools.evaluationTimeout} />
            </Box>
          </Paper>
          {run.data && (
            <Alert severity="success">
              Worker {run.data.worker} executado. Processados: {run.data.processed}.
            </Alert>
          )}
          {run.error && <Alert severity="error">{formatError(run.error)}</Alert>}
        </>
      )}
    </Stack>
  );
}

function MissionNode({
  mission,
  missions,
  onUpdated,
  canConfigure,
  depth = 0,
}: {
  mission: Missao;
  missions: Missao[];
  onUpdated: () => Promise<unknown>;
  canConfigure: boolean;
  depth?: number;
}) {
  const { session, hasAnyRole } = useAuth();
  const [editing, setEditing] = useState(false);
  const [configuringTriggers, setConfiguringTriggers] = useState(false);
  const [form, setForm] = useState({
    titulo: mission.titulo,
    descricao: mission.descricao ?? "",
    tipo: mission.tipo,
    value: String(mission.value),
    ativo: mission.ativo,
    parentId: mission.parentId ?? "",
  });
  const children = missions.filter((item) => item.parentId === mission.id);
  const invalidParentIds = new Set([mission.id, ...collectDescendantIds(mission.id, missions)]);
  const updateMission = useMutation({
    mutationFn: () =>
      apiRequest<Missao>(
        `/api/missoes/${mission.id}`,
        {
          method: "PUT",
          body: JSON.stringify({
            titulo: form.titulo,
            descricao: form.descricao,
            tipo: form.tipo,
            value: Number(form.value),
            ativo: form.ativo,
            parentId: form.parentId || null,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setEditing(false);
      await onUpdated();
    },
  });

  return (
    <Box sx={{ ml: { xs: depth, sm: depth * 3 } }}>
      <Paper variant="outlined" sx={{ p: 2, borderLeft: 4, borderLeftColor: "primary.main" }}>
        <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography fontWeight={700}>{mission.titulo}</Typography>
            <Typography variant="body2" color="text.secondary">{mission.descricao || "Sem descricao"}</Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="flex-start" flexWrap="wrap">
            <Chip label={`${mission.value} XP`} size="small" />
            <ActiveChip active={mission.ativo} />
            {canConfigure && <Button size="small" startIcon={<PlayArrowOutlined />} onClick={() => setConfiguringTriggers(true)}>Configurar gatilhos</Button>}
            <Button size="small" startIcon={<EditOutlined />} onClick={() => setEditing(true)}>Editar</Button>
          </Stack>
        </Stack>
        <Typography variant="caption" color="text.secondary">{mission.tipo} | {children.length} etapa(s) filha(s)</Typography>
      </Paper>
      {children.length > 0 && (
        <Stack spacing={1.5} sx={{ mt: 1.5 }}>
          {children.map((child) => (
            <MissionNode key={child.id} mission={child} missions={missions} onUpdated={onUpdated} canConfigure={canConfigure} depth={depth + 1} />
          ))}
        </Stack>
      )}
      <Dialog open={editing} onClose={() => setEditing(false)} fullWidth maxWidth="sm">
        <DialogTitle>Editar missao</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Titulo" value={form.titulo} onChange={(event) => setForm({ ...form, titulo: event.target.value })} required />
            <TextField label="Descricao" value={form.descricao} onChange={(event) => setForm({ ...form, descricao: event.target.value })} multiline minRows={3} />
            <FormControl>
              <InputLabel>Missao pai</InputLabel>
              <Select label="Missao pai" value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}>
                <MenuItem value="">Nenhuma, missao raiz</MenuItem>
                {missions.filter((item) => !invalidParentIds.has(item.id)).map((item) => (
                  <MenuItem key={item.id} value={item.id}>{item.titulo}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField label="Tipo" value={form.tipo} onChange={(event) => setForm({ ...form, tipo: event.target.value })} />
            <TextField label="XP" type="number" value={form.value} onChange={(event) => setForm({ ...form, value: event.target.value })} inputProps={{ min: 0 }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography>Disponivel para atribuicao</Typography>
              <Switch checked={form.ativo} onChange={(event) => setForm({ ...form, ativo: event.target.checked })} />
            </Stack>
            {updateMission.error && <Alert severity="error">{formatError(updateMission.error)}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(false)}>Cancelar</Button>
          <Button variant="contained" onClick={() => updateMission.mutate()} disabled={updateMission.isPending || !form.titulo.trim()}>
            Salvar alteracoes
          </Button>
        </DialogActions>
      </Dialog>
      <MissionTriggerConfigurator
        mission={mission}
        readOnly={!hasAnyRole("ADMIN")}
        open={configuringTriggers}
        onClose={() => setConfiguringTriggers(false)}
      />
    </Box>
  );
}

function PagedTable<T>({
  query,
  headers,
  emptyText,
  page,
  setPage,
  renderRow,
}: {
  query: { data?: PageResponse<T>; isLoading: boolean; isError: boolean; isFetching: boolean; error: Error | null };
  headers: string[];
  emptyText: string;
  page: number;
  setPage: (page: number) => void;
  renderRow: (item: T) => ReactNode;
}) {
  const rows = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;
  return (
    <>
      {query.isLoading && <CircularProgress size={24} />}
      {query.isError && <Alert severity="error">{formatError(query.error)}</Alert>}
      {!query.isLoading && !query.isError && (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>{headers.map((header) => <TableCell key={header}>{header}</TableCell>)}</TableRow>
              </TableHead>
              <TableBody>
                {rows.map(renderRow)}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={headers.length} align="center">{emptyText}</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={2} sx={{ p: 2 }}>
            <Typography color="text.secondary">Pagina {page + 1} de {Math.max(totalPages, 1)}</Typography>
            <Button disabled={page === 0 || query.isFetching} onClick={() => setPage(Math.max(0, page - 1))}>Anterior</Button>
            <Button disabled={query.isFetching || page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Proxima</Button>
          </Stack>
        </Paper>
      )}
    </>
  );
}

function EventDetails({ event }: { event: EventoDefinicao }) {
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h6">{event.nome}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>{event.id}</Typography>
      </Box>
      <Divider />
      <Detail label="Missao" value={event.missaoTitulo} />
      <Detail label="Disparo" value={event.tipoDisparo} />
      <Detail label="Modo" value={event.modoAvaliacao} />
      <Detail label="Operador" value={event.operadorLogico} />
      <Detail label="Politica" value={event.politicaAtribuicao} />
      <Detail label="Janela" value={optionalNumber(event.janelaSegundos)} />
      <Detail label="Duracao minima" value={optionalNumber(event.duracaoMinimaSegundos)} />
      <Detail label="Quantidade" value={optionalNumber(event.quantidadeNecessaria)} />
      <Typography variant="subtitle2">Condicoes</Typography>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Ordem</TableCell>
              <TableCell>Parametro</TableCell>
              <TableCell>Tipo</TableCell>
              <TableCell>Operador</TableCell>
              <TableCell>Valor</TableCell>
              <TableCell>Obrigatoria</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {event.condicoes.map((condition) => (
              <TableRow key={condition.id}>
                <TableCell>{condition.ordem ?? "-"}</TableCell>
                <TableCell>{condition.parametroNome}</TableCell>
                <TableCell>{condition.parametroDataType}</TableCell>
                <TableCell>{condition.operador}</TableCell>
                <TableCell>{conditionValue(condition)}</TableCell>
                <TableCell>{condition.obrigatoria ? "sim" : "nao"}</TableCell>
              </TableRow>
            ))}
            {event.condicoes.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">Sem condicoes ativas.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}

function WindowDetails({ window, evidences }: { window: MissionWindow; evidences: WindowEvidence[] }) {
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h6">{window.eventoNome}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>{window.id}</Typography>
      </Box>
      <StatusChip status={window.status} />
      <Divider />
      <Detail label="Compartimento" value={window.compartimentoId} />
      <Detail label="Periodo aula" value={window.periodoAulaId ?? "-"} />
      <Detail label="Inicio" value={formatDate(window.inicioEm)} />
      <Detail label="Fim previsto" value={formatDate(window.fimPrevistoEm)} />
      <Detail label="Ultima medicao" value={formatDate(window.ultimaMedicaoEm)} />
      <Detail label="Proxima avaliacao" value={formatDate(window.proximaAvaliacaoEm)} />
      <Detail label="Lease" value={formatDate(window.leaseUntil)} />
      <Detail label="Tentativas" value={String(window.attempts)} />
      <Detail label="Erro" value={window.lastError ?? "-"} />
      <JsonBlock label="Contexto snapshot" value={window.contextoSnapshot} />
      <EvidenceTable evidences={evidences} emptyText="Sem evidencias da janela." />
    </Stack>
  );
}

function OccurrenceDetails({ occurrence, evidences }: { occurrence: MissionOccurrence; evidences: OccurrenceEvidence[] }) {
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h6">{occurrence.eventoNome}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>{occurrence.id}</Typography>
      </Box>
      <StatusChip status={occurrence.status} />
      <Divider />
      <Detail label="Compartimento" value={occurrence.compartimentoId ?? "-"} />
      <Detail label="Sensor" value={occurrence.sensorExternalId ?? "-"} />
      <Detail label="Inicio" value={formatDate(occurrence.inicioEm)} />
      <Detail label="Fim" value={formatDate(occurrence.fimEm)} />
      <Detail label="Detectado" value={formatDate(occurrence.detectadoEm)} />
      <Detail label="Chave idempotencia" value={occurrence.chaveIdempotencia} />
      <Detail label="Fingerprint" value={occurrence.conteudoFingerprint ?? "-"} />
      <JsonBlock label="Contexto snapshot" value={occurrence.contextoSnapshot} />
      <EvidenceTable evidences={evidences} emptyText="Sem evidencias da ocorrencia." />
    </Stack>
  );
}

function EvidenceTable({ evidences, emptyText }: { evidences: Array<WindowEvidence | OccurrenceEvidence>; emptyText: string }) {
  return (
    <Box>
      <Typography variant="subtitle2">Evidencias</Typography>
      <TableContainer component={Paper} variant="outlined" sx={{ mt: 1 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Papel</TableCell>
              <TableCell>Medicao</TableCell>
              <TableCell>Parametro valor</TableCell>
              <TableCell>Criado</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {evidences.map((evidence) => (
              <TableRow key={evidence.id}>
                <TableCell>{evidence.papel}</TableCell>
                <TableCell sx={{ overflowWrap: "anywhere" }}>{evidence.medicaoId}</TableCell>
                <TableCell sx={{ overflowWrap: "anywhere" }}>{evidence.parametroValorId ?? "-"}</TableCell>
                <TableCell>{formatDate(evidence.createdAt)}</TableCell>
              </TableRow>
            ))}
            {evidences.length === 0 && (
              <TableRow><TableCell colSpan={4} align="center">{emptyText}</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}

function WindowOperationButtons({ window, onOperate }: { window: MissionWindow; onOperate: (type: WindowOperation) => void }) {
  return (
    <>
      <Tooltip title="Retry">
        <IconButton aria-label={`Retry da janela ${window.id}`} onClick={() => onOperate("retry")}><RefreshOutlined /></IconButton>
      </Tooltip>
      <Tooltip title="Satisfazer">
        <IconButton aria-label={`Satisfazer janela ${window.id}`} onClick={() => onOperate("satisfy")}><PlayArrowOutlined /></IconButton>
      </Tooltip>
      <Button size="small" onClick={() => onOperate("invalidate")}>Invalidar</Button>
      <Button size="small" onClick={() => onOperate("expire")}>Expirar</Button>
      <Button size="small" color="error" onClick={() => onOperate("fail")}>Falhar</Button>
    </>
  );
}

function WindowOperationDialog({
  operation,
  mutate,
  pending,
  error,
  onClose,
}: {
  operation: { window: MissionWindow; type: WindowOperation } | null;
  mutate: (payload: { type: WindowOperation; reason: string }) => void;
  pending: boolean;
  error: Error | null;
  onClose: () => void;
}) {
  const [reason, setReason] = useState("");
  const title = operation ? `Executar ${operation.type}` : "Operar janela";
  return (
    <Dialog open={Boolean(operation)} onClose={() => !pending && onClose()} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>{operation?.window.id}</Typography>
          {operation?.type !== "satisfy" && (
            <TextField label="Motivo" value={reason} onChange={(event) => setReason(event.target.value)} multiline minRows={3} />
          )}
          {error && <Alert severity="error">{formatError(error)}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={pending}>Cancelar</Button>
        <Button
          variant="contained"
          onClick={() => {
            if (!operation) return;
            mutate({ type: operation.type, reason });
            setReason("");
          }}
          disabled={pending}
        >
          Confirmar
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function WorkerCard({
  title,
  state,
  canOperate,
  pending,
  onRun,
}: {
  title: string;
  state: { enabled: boolean; fixedDelay: string; batchSize: number; leaseDuration: string; maxAttempts: number; backlog: number };
  canOperate: boolean;
  pending: boolean;
  onRun: () => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" gap={2}>
        <Box>
          <Typography variant="h6">{title}</Typography>
          <ActiveChip active={state.enabled} activeLabel="Habilitado" inactiveLabel="Desabilitado" />
        </Box>
        <Button startIcon={<PlayArrowOutlined />} variant="contained" disabled={!canOperate || pending} onClick={onRun}>
          Executar lote
        </Button>
      </Stack>
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" }, gap: 2, mt: 2 }}>
        <Detail label="Backlog" value={String(state.backlog)} />
        <Detail label="Batch" value={String(state.batchSize)} />
        <Detail label="Delay" value={state.fixedDelay} />
        <Detail label="Lease" value={state.leaseDuration} />
        <Detail label="Max tentativas" value={String(state.maxAttempts)} />
      </Box>
    </Paper>
  );
}

function DetailsPane({ loading, error, children }: { loading: boolean; error: Error | null; children: ReactNode }) {
  return (
    <Box sx={{ width: { xs: "100vw", sm: 680 }, p: 3 }}>
      {loading && <CircularProgress size={24} />}
      {error && <Alert severity="error">{formatError(error)}</Alert>}
      {!loading && !error && children}
    </Box>
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
          <InputAdornment position="start"><SearchOutlined /></InputAdornment>
        ),
      }}
    />
  );
}

function EnumSelect({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  const labelId = useId();
  const selectId = useId();
  return (
    <FormControl sx={{ minWidth: 190 }}>
      <InputLabel id={labelId}>{label}</InputLabel>
      <Select id={selectId} labelId={labelId} label={label} value={value} onChange={(event) => onChange(event.target.value)}>
        <MenuItem value="">Todos</MenuItem>
        {options.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
      </Select>
    </FormControl>
  );
}

function ActiveChip({ active, activeLabel = "Ativa", inactiveLabel = "Inativa" }: { active: boolean; activeLabel?: string; inactiveLabel?: string }) {
  return <Chip label={active ? activeLabel : inactiveLabel} size="small" color={active ? "success" : "default"} />;
}

function StatusChip({ status }: { status: string }) {
  const color = status === "COMPLETED" || status === "SATISFEITA" || status === "CONFIRMADO" || status === "PROCESSADO"
    ? "success"
    : status === "FAILED" || status === "INVALIDADA" || status === "EXPIRADA" || status === "DESCARTADO"
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

function JsonBlock({ label, value }: { label: string; value?: string | null }) {
  return (
    <Box>
      <Typography variant="subtitle2">{label}</Typography>
      <Box
        component="pre"
        aria-label={`${label} readonly`}
        sx={{
          bgcolor: "grey.100",
          border: 1,
          borderColor: "divider",
          borderRadius: 1,
          maxHeight: 260,
          overflow: "auto",
          p: 1.5,
          whiteSpace: "pre-wrap",
          overflowWrap: "anywhere",
          fontSize: 13,
        }}
      >
        {prettyJson(value)}
      </Box>
    </Box>
  );
}

function collectDescendantIds(parentId: string, missions: Missao[]): string[] {
  const children = missions.filter((mission) => mission.parentId === parentId);
  return children.flatMap((child) => [child.id, ...collectDescendantIds(child.id, missions)]);
}

function conditionValue(condition: EventoDefinicao["condicoes"][number]) {
  if (condition.valorNumeric2 !== null && condition.valorNumeric2 !== undefined) {
    return `${condition.valorNumeric1 ?? "-"} .. ${condition.valorNumeric2}`;
  }
  if (condition.valorNumeric1 !== null && condition.valorNumeric1 !== undefined) return String(condition.valorNumeric1);
  if (condition.valorBoolean !== null && condition.valorBoolean !== undefined) return String(condition.valorBoolean);
  return condition.valorText ?? "-";
}

function optionalNumber(value?: number | null) {
  return value === null || value === undefined ? "-" : String(value);
}

function formatDate(value?: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function prettyJson(value?: string | null) {
  if (!value) return "{}";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatError(error: unknown) {
  if (error instanceof ApiError && error.code) return `${error.code}: ${error.message}`;
  if (error instanceof Error) return error.message;
  return "Falha inesperada";
}
