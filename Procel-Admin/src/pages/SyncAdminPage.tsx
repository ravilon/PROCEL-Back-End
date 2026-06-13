import {
  ApartmentOutlined,
  CalendarMonthOutlined,
  RefreshOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  LinearProgress,
  Paper,
  Stack,
  TextField,
  Typography,
  createFilterOptions,
} from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { ApiError, apiRequest } from "../lib/api";
import type { AulasSyncJob, Compartimento, RoomsSyncResult } from "../types";

function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

const activeStatuses = new Set(["PENDING", "RUNNING"]);
const JOB_STORAGE_PREFIX = "procel:aulas-sync-job:";
const filterRooms = createFilterOptions<Compartimento>({
  stringify: (room) =>
    [
      room.id,
      room.nome,
      room.tipo,
      room.predioNome,
      room.campusNome,
      room.unidadeNome,
    ]
      .filter(Boolean)
      .join(" "),
});

export function SyncAdminPage() {
  const { session } = useAuth();
  const jobStorageKey = `${JOB_STORAGE_PREFIX}${session?.userId ?? "anonymous"}`;
  const [weekStart, setWeekStart] = useState(todayIso);
  const [roomId, setRoomId] = useState("");
  const [jobId, setJobId] = useState<string | null>(() =>
    localStorage.getItem(jobStorageKey),
  );

  const rooms = useQuery({
    queryKey: ["sync", "rooms"],
    queryFn: () =>
      apiRequest<Compartimento[]>("/api/catalog/compartimentos", {}, session),
  });
  const selectedRoom =
    rooms.data?.find((room) => room.id === roomId) ?? null;

  const roomsSync = useMutation({
    mutationFn: () =>
      apiRequest<RoomsSyncResult>("/api/rooms/sync", { method: "POST" }, session),
  });

  const startPeriodsSync = useMutation({
    mutationFn: (selectedRoomId?: string) => {
      const params = new URLSearchParams({ weekStart });
      if (selectedRoomId) params.set("roomId", selectedRoomId);
      return apiRequest<AulasSyncJob>(
        `/api/rooms/aulas/sync?${params.toString()}`,
        { method: "POST" },
        session,
      );
    },
    onSuccess: (startedJob) => {
      localStorage.setItem(jobStorageKey, startedJob.jobId);
      setJobId(startedJob.jobId);
    },
  });

  const activeJob = useQuery({
    queryKey: ["sync", "aulas-job", "active"],
    queryFn: async () => {
      const found = await apiRequest<AulasSyncJob | undefined>(
        "/api/rooms/aulas/sync/active",
        {},
        session,
      );
      if (found) localStorage.setItem(jobStorageKey, found.jobId);
      return found;
    },
    enabled: !jobId,
    refetchInterval: jobId ? false : 3000,
  });

  const effectiveJobId = jobId ?? activeJob.data?.jobId ?? null;
  const job = useQuery({
    queryKey: ["sync", "aulas-job", effectiveJobId],
    queryFn: async () => {
      try {
        return await apiRequest<AulasSyncJob>(
          `/api/rooms/aulas/sync/${effectiveJobId}`,
          {},
          session,
        );
      } catch (error) {
        if (error instanceof ApiError && error.status === 400) {
          localStorage.removeItem(jobStorageKey);
          return apiRequest<AulasSyncJob | undefined>(
            "/api/rooms/aulas/sync/active",
            {},
            session,
          );
        }
        throw error;
      }
    },
    enabled: Boolean(effectiveJobId),
    refetchInterval: (query) =>
      activeStatuses.has(query.state.data?.status ?? "") ? 1500 : false,
  });

  const currentJob = job.data ?? activeJob.data;
  const isPeriodsSyncing =
    startPeriodsSync.isPending || activeStatuses.has(currentJob?.status ?? "");

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Sincronizações</Typography>
        <Typography color="text.secondary">
          Importe salas e períodos de aula com acompanhamento do processamento.
        </Typography>
      </Box>

      <Card>
        <CardContent>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={2}
            alignItems={{ sm: "center" }}
            justifyContent="space-between"
          >
            <Box>
              <Typography variant="h6">Salas e compartimentos</Typography>
              <Typography variant="body2" color="text.secondary">
                Atualiza campus, prédios, unidades e salas usando a fonte configurada.
              </Typography>
            </Box>
            <Button
              variant="contained"
              startIcon={<ApartmentOutlined />}
              onClick={() => roomsSync.mutate()}
              disabled={roomsSync.isPending}
              sx={{ flexShrink: 0 }}
            >
              {roomsSync.isPending ? "Sincronizando..." : "Sincronizar salas"}
            </Button>
          </Stack>
          {roomsSync.isError && (
            <Alert severity="error" sx={{ mt: 2 }}>{roomsSync.error.message}</Alert>
          )}
          {roomsSync.isPending && <LinearProgress sx={{ mt: 2 }} />}
          {roomsSync.data && (
            <Alert severity="success" sx={{ mt: 2 }}>
              {roomsSync.data.fetched} recebidas, {roomsSync.data.inserted} inseridas,{" "}
              {roomsSync.data.updated} atualizadas e {roomsSync.data.skipped} ignoradas.
            </Alert>
          )}
        </CardContent>
      </Card>

      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6">Períodos de aula</Typography>
            <Typography variant="body2" color="text.secondary">
              Sincronize todas as salas da semana ou somente uma sala.
            </Typography>
          </Box>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <TextField
              label="Data da semana"
              type="date"
              value={weekStart}
              onChange={(event) => setWeekStart(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ minWidth: { md: 210 } }}
            />
            <Autocomplete
              fullWidth
              options={rooms.data ?? []}
              value={selectedRoom}
              loading={rooms.isLoading}
              filterOptions={filterRooms}
              getOptionLabel={(room) => `${room.nome} (${room.id})`}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              onChange={(_, room) => setRoomId(room?.id ?? "")}
              noOptionsText="Nenhuma sala encontrada"
              loadingText="Carregando salas..."
              renderOption={(props, room) => (
                <Box component="li" {...props} key={room.id}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {room.nome} ({room.id})
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {[room.tipo, room.predioNome, room.campusNome, room.unidadeNome]
                        .filter(Boolean)
                        .join(" - ")}
                    </Typography>
                  </Box>
                </Box>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Sala para sincronização individual"
                  placeholder="Busque por sala, ID, prédio, campus ou unidade"
                  error={rooms.isError}
                  helperText={
                    rooms.isError
                      ? `Não foi possível carregar as salas: ${rooms.error.message}`
                      : "Digite para filtrar as salas disponíveis."
                  }
                />
              )}
            />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
            <Button
              variant="contained"
              startIcon={<CalendarMonthOutlined />}
              onClick={() => startPeriodsSync.mutate(undefined)}
              disabled={!weekStart || isPeriodsSyncing}
            >
              Sincronizar semana completa
            </Button>
            <Button
              variant="outlined"
              startIcon={<RefreshOutlined />}
              onClick={() => startPeriodsSync.mutate(roomId)}
              disabled={!weekStart || !roomId || isPeriodsSyncing}
            >
              Sincronizar sala selecionada
            </Button>
          </Stack>

          {startPeriodsSync.isError && (
            <Alert severity="error">{startPeriodsSync.error.message}</Alert>
          )}
          {job.isError && <Alert severity="error">{job.error.message}</Alert>}

          {currentJob && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={1.5}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  justifyContent="space-between"
                  spacing={1}
                >
                  <Box>
                    <Typography fontWeight={600}>Semana de {currentJob.weekStart}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {currentJob.roomId
                        ? `Sala ${currentJob.roomId}`
                        : "Todas as salas elegíveis"}
                    </Typography>
                  </Box>
                  <Chip
                    label={currentJob.status}
                    color={
                      currentJob.status === "COMPLETED"
                        ? "success"
                        : currentJob.status === "FAILED"
                          ? "error"
                          : "primary"
                    }
                  />
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={currentJob.progress.progressPercent}
                />
                <Typography variant="body2">
                  {currentJob.progress.roomsProcessed} de {currentJob.progress.roomsTotal} salas
                  processadas ({currentJob.progress.progressPercent}%)
                </Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip
                    size="small"
                    color="success"
                    label={`${currentJob.progress.roomsSynced} com sucesso`}
                  />
                  <Chip
                    size="small"
                    color={currentJob.progress.roomsFailed ? "error" : "default"}
                    label={`${currentJob.progress.roomsFailed} com falha`}
                  />
                  {currentJob.result && (
                    <Chip
                      size="small"
                      label={`${currentJob.result.occurrencesInserted} períodos inseridos`}
                    />
                  )}
                </Stack>
                {currentJob.error && <Alert severity="error">{currentJob.error}</Alert>}
                {currentJob.result?.errors.map((error) => (
                  <Alert key={error} severity="warning">{error}</Alert>
                ))}
              </Stack>
            </Paper>
          )}
        </Stack>
      </Paper>
    </Stack>
  );
}
