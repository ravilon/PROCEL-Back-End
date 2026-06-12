import {
  ApartmentOutlined,
  AddOutlined,
  CheckCircleOutlined,
  ErrorOutline,
  EditOutlined,
  ExpandLessOutlined,
  ExpandMoreOutlined,
  HelpOutline,
  MenuBookOutlined,
  PersonSearchOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Alert,
  LinearProgress,
  Box,
  Chip,
  Checkbox,
  Collapse,
  CircularProgress,
  Button,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type {
  Atividade,
  AtividadeStatus,
  Compartimento,
  Disciplina,
  DisciplinaAluno,
  Curso,
  GrupoRegra,
  Medicao,
  PeriodoAula,
  Pessoa,
  PessoaResumo,
  PessoaCurso,
  Sensor,
  TipoSensor,
} from "../types";

type MeasurementStatus = "ALL" | "APPROVED" | "REJECTED" | "UNCLASSIFIED";

function toDateTimeLocal(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function measurementStatus(measurement: Medicao): Exclude<MeasurementStatus, "ALL"> {
  const results = Object.values(measurement.qualificacoes).flat().map((item) => item.resultado);
  if (results.some((result) => ["ALERTA", "CRITICO", "INVALIDO"].includes(result))) {
    return "REJECTED";
  }
  if (results.some((result) => ["IDEAL", "NORMAL"].includes(result))) {
    return "APPROVED";
  }
  return "UNCLASSIFIED";
}

const measurementStatusConfig = {
  APPROVED: { label: "Aprovada", color: "success" as const, icon: <CheckCircleOutlined /> },
  REJECTED: { label: "Reprovada", color: "error" as const, icon: <ErrorOutline /> },
  UNCLASSIFIED: { label: "Sem classificação", color: "default" as const, icon: <HelpOutline /> },
};

function ErrorAlert({ error }: { error: Error | null }) {
  return error ? <Alert severity="error">{error.message}</Alert> : null;
}

function Empty({ text }: { text: string }) {
  return (
    <Typography color="text.secondary" sx={{ p: 2, textAlign: "center" }}>
      {text}
    </Typography>
  );
}

function CompartimentosTree() {
  const { session, hasAnyRole } = useAuth();
  const [query, setQuery] = useState("");
  const [roomFilters, setRoomFilters] = useState({
    tipo: "",
    predio: "",
    unidade: "",
    campus: "",
  });
  const [selectedRoomIds, setSelectedRoomIds] = useState<string[]>([]);
  const [bulkRulesOpen, setBulkRulesOpen] = useState(false);
  const [bulkGroupId, setBulkGroupId] = useState("");
  const [bulkStatus, setBulkStatus] = useState("ATIVO");
  const [selectedRoom, setSelectedRoom] = useState<Compartimento | null>(null);
  const [selectedSensor, setSelectedSensor] = useState<Sensor | null>(null);
  const [sensorDialogOpen, setSensorDialogOpen] = useState(false);
  const [newSensor, setNewSensor] = useState({ externalId: "", nome: "", tipoNome: "" });
  const [measurementFrom, setMeasurementFrom] = useState(() =>
    toDateTimeLocal(new Date(Date.now() - 24 * 60 * 60 * 1000)),
  );
  const [measurementTo, setMeasurementTo] = useState(() => toDateTimeLocal(new Date()));
  const [measurementStatusFilter, setMeasurementStatusFilter] =
    useState<MeasurementStatus>("ALL");
  const [measurementPage, setMeasurementPage] = useState(0);
  const [measurementsExpanded, setMeasurementsExpanded] = useState(true);
  const measurementLimit = 24;
  const queryClient = useQueryClient();

  const rooms = useQuery({
    queryKey: ["catalog", "rooms", query, roomFilters],
    queryFn: () =>
      apiRequest<Compartimento[]>(
        `/api/catalog/compartimentos?${new URLSearchParams({
          q: query,
          ...roomFilters,
        }).toString()}`,
        {},
        session,
      ),
  });
  const ruleGroups = useQuery({
    queryKey: ["rules", "groups"],
    queryFn: () => apiRequest<GrupoRegra[]>("/api/rules/groups", {}, session),
    enabled: bulkRulesOpen,
  });
  const bulkAssignRules = useMutation({
    mutationFn: () =>
      apiRequest<unknown>(
        `/api/rules/groups/${bulkGroupId}/rooms`,
        {
          method: "POST",
          body: JSON.stringify({
            compartimentoIds: selectedRoomIds,
            status: bulkStatus,
            validoDe: null,
            validoAte: null,
          }),
        },
        session,
      ),
    onSuccess: () => {
      setBulkRulesOpen(false);
      setSelectedRoomIds([]);
      setBulkGroupId("");
    },
  });

  const sensors = useQuery({
    queryKey: ["catalog", "room-sensors", selectedRoom?.id],
    queryFn: () =>
      apiRequest<Sensor[]>(
        `/api/catalog/compartimentos/${selectedRoom!.id}/sensores`,
        {},
        session,
      ),
    enabled: Boolean(selectedRoom && hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")),
  });

  const periods = useQuery({
    queryKey: ["catalog", "room-periods", selectedRoom?.id],
    queryFn: () =>
      apiRequest<PeriodoAula[]>(
        `/api/catalog/compartimentos/${selectedRoom!.id}/periodos-aula`,
        {},
        session,
      ),
    enabled: Boolean(selectedRoom),
  });

  const measurements = useQuery({
    queryKey: [
      "measurements",
      selectedSensor?.externalId,
      measurementFrom,
      measurementTo,
      measurementPage,
    ],
    queryFn: () =>
      apiRequest<Medicao[]>(
        `/api/sensors/${selectedSensor!.externalId}/medicoes?from=${encodeURIComponent(
          new Date(measurementFrom).toISOString(),
        )}&to=${encodeURIComponent(new Date(measurementTo).toISOString())}&page=${
          measurementPage
        }&limit=${measurementLimit}`,
        {},
        session,
      ),
    enabled: Boolean(selectedSensor && measurementFrom && measurementTo),
  });

  const sensorTypes = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => apiRequest<TipoSensor[]>("/api/sensor-admin/types", {}, session),
    enabled: sensorDialogOpen || Boolean(selectedSensor),
  });

  const visibleMeasurements = measurements.data?.filter(
    (measurement) =>
      measurementStatusFilter === "ALL"
      || measurementStatus(measurement) === measurementStatusFilter,
  );
  const selectedSensorType = sensorTypes.data?.find(
    (type) => type.nome === selectedSensor?.tipoNome,
  );

  const createSensor = useMutation({
    mutationFn: () =>
      apiRequest<Sensor>(
        "/api/sensor-admin/sensors",
        {
          method: "POST",
          body: JSON.stringify({
            ...newSensor,
            compartimentoId: selectedRoom!.id,
          }),
        },
        session,
      ),
    onSuccess: async (sensor) => {
      setSensorDialogOpen(false);
      setNewSensor({ externalId: "", nome: "", tipoNome: "" });
      setSelectedSensor(sensor);
      await queryClient.invalidateQueries({
        queryKey: ["catalog", "room-sensors", selectedRoom?.id],
      });
    },
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", md: "repeat(5, 1fr)" },
            gap: 1.5,
          }}
        >
          <TextField
            label="Buscar compartimento"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="ID ou nome"
          />
          {(["tipo", "predio", "unidade", "campus"] as const).map((field) => (
            <TextField
              key={field}
              label={field.charAt(0).toUpperCase() + field.slice(1)}
              value={roomFilters[field]}
              onChange={(event) =>
                setRoomFilters({ ...roomFilters, [field]: event.target.value })
              }
            />
          ))}
        </Box>
        {hasAnyRole("ADMIN", "OPERADOR") && selectedRoomIds.length > 0 && (
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mt: 2 }}>
            <Chip label={`${selectedRoomIds.length} sala(s) selecionada(s)`} />
            <Button variant="contained" onClick={() => setBulkRulesOpen(true)}>
              Associar grupo de regras
            </Button>
            <Button onClick={() => setSelectedRoomIds([])}>Limpar seleção</Button>
          </Stack>
        )}
      </Paper>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", lg: "320px minmax(0, 1fr)" },
          gap: 2,
        }}
      >
        <Paper variant="outlined" sx={{ maxHeight: 680, overflow: "auto" }}>
          {rooms.isLoading && <CircularProgress sx={{ m: 2 }} />}
          <ErrorAlert error={rooms.error} />
          <List dense>
            {rooms.data?.map((room) => (
              <ListItemButton
                key={room.id}
                selected={selectedRoom?.id === room.id}
                onClick={() => {
                  setSelectedRoom(room);
                  setSelectedSensor(null);
                }}
              >
                {hasAnyRole("ADMIN", "OPERADOR") && (
                  <Checkbox
                    edge="start"
                    checked={selectedRoomIds.includes(room.id)}
                    onClick={(event) => event.stopPropagation()}
                    onChange={(_, checked) =>
                      setSelectedRoomIds((current) =>
                        checked
                          ? [...current, room.id]
                          : current.filter((id) => id !== room.id),
                      )
                    }
                  />
                )}
                <ListItemText
                  primary={room.nome}
                  secondary={`${room.id} · ${room.predioNome}`}
                />
              </ListItemButton>
            ))}
          </List>
        </Paper>

        <Stack spacing={2} sx={{ minWidth: 0 }}>
          {!selectedRoom && (
            <Paper variant="outlined">
              <Empty text="Selecione um compartimento para navegar nas relacoes." />
            </Paper>
          )}
          {selectedRoom && (
            <>
              <Paper sx={{ p: 2 }} variant="outlined">
                <Typography variant="h6">{selectedRoom.nome}</Typography>
                <Typography color="text.secondary">
                  {selectedRoom.id} · {selectedRoom.tipo} · {selectedRoom.campusNome}
                </Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                  <Chip label={selectedRoom.predioNome} size="small" />
                  <Chip label={selectedRoom.unidadeNome} size="small" />
                  {selectedRoom.capacidade != null && (
                    <Chip label={`Capacidade: ${selectedRoom.capacidade}`} size="small" />
                  )}
                </Stack>
              </Paper>

              {hasAnyRole("ADMIN", "OPERADOR", "ANALISTA") && (
                <Paper variant="outlined">
                  <Box sx={{ p: 2 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="h6">
                        <SensorsOutlined sx={{ verticalAlign: "middle", mr: 1 }} />
                        Sensores
                      </Typography>
                      {hasAnyRole("ADMIN", "OPERADOR") && (
                        <Button
                          size="small"
                          startIcon={<AddOutlined />}
                          onClick={() => setSensorDialogOpen(true)}
                        >
                          Cadastrar
                        </Button>
                      )}
                    </Stack>
                  </Box>
                  <Divider />
                  <Box
                    sx={{
                      p: 2,
                      display: "grid",
                      gridTemplateColumns: {
                        xs: "1fr",
                        md: "repeat(2, minmax(0, 1fr))",
                        xl: "repeat(3, minmax(0, 1fr))",
                      },
                      gap: 1.5,
                    }}
                  >
                    {sensors.data?.map((sensor) => (
                      <Paper
                        key={sensor.externalId}
                        variant="outlined"
                        onClick={() => {
                          setSelectedSensor(sensor);
                          setMeasurementPage(0);
                          setMeasurementsExpanded(true);
                        }}
                        sx={{
                          p: 2,
                          cursor: "pointer",
                          borderColor:
                            selectedSensor?.externalId === sensor.externalId
                              ? "primary.main"
                              : "divider",
                          bgcolor:
                            selectedSensor?.externalId === sensor.externalId
                              ? "action.selected"
                              : "background.paper",
                        }}
                      >
                        <Typography fontWeight={700}>{sensor.nome}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {sensor.externalId}
                        </Typography>
                        <Chip label={sensor.tipoNome} size="small" sx={{ mt: 1 }} />
                        <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                          {sensor.compartimentoNome}
                        </Typography>
                      </Paper>
                    ))}
                  </Box>
                  {!sensors.isLoading && sensors.data?.length === 0 && (
                    <Empty text="Nenhum sensor vinculado." />
                  )}
                  <ErrorAlert error={sensors.error} />
                </Paper>
              )}

              {selectedSensor && (
                <Stack spacing={2}>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack
                      direction={{ xs: "column", md: "row" }}
                      justifyContent="space-between"
                      spacing={2}
                    >
                      <Box>
                        <Typography variant="h6">{selectedSensor.nome}</Typography>
                        <Typography color="text.secondary">
                          {selectedSensor.externalId} · {selectedSensor.tipoNome}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        {selectedSensorType?.parametros.map((parameter) => (
                          <Chip
                            key={parameter.id}
                            label={`${parameter.nome}${
                              parameter.numericUnit ? ` (${parameter.numericUnit})` : ""
                            }`}
                            size="small"
                            variant="outlined"
                          />
                        ))}
                      </Stack>
                    </Stack>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="h6">Medições</Typography>
                      <IconButton
                        aria-label={measurementsExpanded ? "Minimizar medições" : "Exibir medições"}
                        onClick={() => setMeasurementsExpanded((expanded) => !expanded)}
                      >
                        {measurementsExpanded ? <ExpandLessOutlined /> : <ExpandMoreOutlined />}
                      </IconButton>
                    </Stack>
                    <Collapse in={measurementsExpanded}>
                    <Box
                      sx={{
                        mt: 2,
                        display: "grid",
                        gridTemplateColumns: {
                          xs: "1fr",
                          sm: "repeat(2, 1fr)",
                          lg: "repeat(4, 1fr)",
                        },
                        gap: 1.5,
                      }}
                    >
                      <TextField
                        label="Início"
                        type="datetime-local"
                        value={measurementFrom}
                        onChange={(event) => {
                          setMeasurementFrom(event.target.value);
                          setMeasurementPage(0);
                        }}
                        slotProps={{ inputLabel: { shrink: true } }}
                      />
                      <TextField
                        label="Fim"
                        type="datetime-local"
                        value={measurementTo}
                        onChange={(event) => {
                          setMeasurementTo(event.target.value);
                          setMeasurementPage(0);
                        }}
                        slotProps={{ inputLabel: { shrink: true } }}
                      />
                      <FormControl>
                        <InputLabel>Classificação</InputLabel>
                        <Select
                          label="Classificação"
                          value={measurementStatusFilter}
                          onChange={(event) =>
                            setMeasurementStatusFilter(event.target.value as MeasurementStatus)
                          }
                        >
                          <MenuItem value="ALL">Todas</MenuItem>
                          <MenuItem value="APPROVED">Aprovadas</MenuItem>
                          <MenuItem value="REJECTED">Reprovadas</MenuItem>
                          <MenuItem value="UNCLASSIFIED">Sem classificação</MenuItem>
                        </Select>
                      </FormControl>
                      <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                        {(["APPROVED", "REJECTED", "UNCLASSIFIED"] as const).map((status) => (
                          <Chip
                            key={status}
                            size="small"
                            color={measurementStatusConfig[status].color}
                            label={`${
                              measurements.data?.filter(
                                (measurement) => measurementStatus(measurement) === status,
                              ).length ?? 0
                            } ${measurementStatusConfig[status].label.toLowerCase()}`}
                          />
                        ))}
                      </Stack>
                    </Box>
                    </Collapse>
                  </Paper>

                  <Collapse in={measurementsExpanded}>
                  <Stack spacing={2}>
                  <ErrorAlert error={measurements.error} />
                  <Box
                    sx={{
                      display: "grid",
                      gridTemplateColumns: {
                        xs: "1fr",
                        xl: "repeat(2, minmax(0, 1fr))",
                      },
                      gap: 2,
                    }}
                  >
                    {visibleMeasurements?.map((measurement) => (
                      <MeasurementCard
                        key={measurement.id}
                        measurement={measurement}
                        parameters={selectedSensorType?.parametros ?? []}
                      />
                    ))}
                  </Box>
                  {!measurements.isLoading && visibleMeasurements?.length === 0 && (
                    <Paper variant="outlined">
                      <Empty text="Nenhuma medição encontrada para os filtros selecionados." />
                    </Paper>
                  )}
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Button
                      disabled={measurementPage === 0 || measurements.isFetching}
                      onClick={() => setMeasurementPage((page) => Math.max(0, page - 1))}
                    >
                      Página anterior
                    </Button>
                    <Typography color="text.secondary">Página {measurementPage + 1}</Typography>
                    <Button
                      disabled={
                        measurements.isFetching
                        || (measurements.data?.length ?? 0) < measurementLimit
                      }
                      onClick={() => setMeasurementPage((page) => page + 1)}
                    >
                      Próxima página
                    </Button>
                  </Stack>
                  </Stack>
                  </Collapse>
                </Stack>
              )}

              <Paper variant="outlined">
                <Box sx={{ p: 2 }}>
                  <Typography variant="h6">Periodos de aula recentes</Typography>
                </Box>
                <PeriodsTable data={periods.data} loading={periods.isLoading} />
                <ErrorAlert error={periods.error} />
              </Paper>
            </>
          )}
        </Stack>
      </Box>
      <Dialog
        open={bulkRulesOpen}
        onClose={() => setBulkRulesOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Associar regras às salas selecionadas</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="info">
              O grupo será associado apenas aos sensores compatíveis com o tipo dos parâmetros.
            </Alert>
            <FormControl required>
              <InputLabel>Grupo de regras</InputLabel>
              <Select
                label="Grupo de regras"
                value={bulkGroupId}
                onChange={(event) => setBulkGroupId(event.target.value)}
              >
                {ruleGroups.data?.map((group) => (
                  <MenuItem key={group.id} value={group.id}>{group.nome}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel>Status do vínculo</InputLabel>
              <Select
                label="Status do vínculo"
                value={bulkStatus}
                onChange={(event) => setBulkStatus(event.target.value)}
              >
                <MenuItem value="ATIVO">Ativo</MenuItem>
                <MenuItem value="AGENDADO">Agendado</MenuItem>
                <MenuItem value="RASCUNHO">Rascunho</MenuItem>
              </Select>
            </FormControl>
            <ErrorAlert error={bulkAssignRules.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkRulesOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => bulkAssignRules.mutate()}
            disabled={bulkAssignRules.isPending || !bulkGroupId}
          >
            Associar
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={sensorDialogOpen}
        onClose={() => setSensorDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Cadastrar sensor em {selectedRoom?.nome}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Identificador externo"
              value={newSensor.externalId}
              onChange={(event) =>
                setNewSensor({ ...newSensor, externalId: event.target.value })
              }
              required
            />
            <TextField
              label="Nome"
              value={newSensor.nome}
              onChange={(event) => setNewSensor({ ...newSensor, nome: event.target.value })}
              required
            />
            <FormControl required>
              <InputLabel>Tipo de sensor</InputLabel>
              <Select
                label="Tipo de sensor"
                value={newSensor.tipoNome}
                onChange={(event) =>
                  setNewSensor({ ...newSensor, tipoNome: event.target.value })
                }
              >
                {sensorTypes.data?.map((item) => (
                  <MenuItem key={item.nome} value={item.nome}>{item.nome}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <ErrorAlert error={createSensor.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSensorDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => createSensor.mutate()}
            disabled={
              createSensor.isPending
              || !newSensor.externalId.trim()
              || !newSensor.nome.trim()
              || !newSensor.tipoNome
            }
          >
            Cadastrar
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function MeasurementCard({
  measurement,
  parameters,
}: {
  measurement: Medicao;
  parameters: TipoSensor["parametros"];
}) {
  const status = measurementStatus(measurement);
  const statusConfig = measurementStatusConfig[status];

  return (
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ p: 2, bgcolor: "grey.50" }}
      >
        <Box>
          <Typography fontWeight={700}>
            {new Date(measurement.timestamp).toLocaleString()}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Origem: {measurement.source}
          </Typography>
        </Box>
        <Chip
          icon={statusConfig.icon}
          label={statusConfig.label}
          color={statusConfig.color}
          variant={status === "UNCLASSIFIED" ? "outlined" : "filled"}
        />
      </Stack>
      <Divider />
      <Box
        sx={{
          p: 2,
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" },
          gap: 1.5,
        }}
      >
        {Object.entries(measurement.valores).map(([name, value]) => {
          const parameter = parameters.find((item) => item.nome === name);
          const qualifications = measurement.qualificacoes[name] ?? [];
          const rejected = qualifications.some((item) =>
            ["ALERTA", "CRITICO", "INVALIDO"].includes(item.resultado),
          );
          const approved = qualifications.some((item) =>
            ["IDEAL", "NORMAL"].includes(item.resultado),
          );

          return (
            <Box
              key={name}
              sx={{
                p: 1.5,
                border: 1,
                borderColor: rejected
                  ? "error.light"
                  : approved
                    ? "success.light"
                    : "divider",
                borderRadius: 1,
              }}
            >
              <Typography variant="caption" color="text.secondary">
                {parameter?.descricao || name}
              </Typography>
              <Typography variant="h6">
                {String(value)}
                {parameter?.numericUnit ? (
                  <Typography component="span" variant="body2" sx={{ ml: 0.5 }}>
                    {parameter.numericUnit}
                  </Typography>
                ) : null}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {name}
              </Typography>
              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                {qualifications.map((qualification) => (
                  <Chip
                    key={qualification.id}
                    size="small"
                    color={
                      ["ALERTA", "CRITICO", "INVALIDO"].includes(qualification.resultado)
                        ? "error"
                        : "success"
                    }
                    label={`${qualification.resultado}: ${
                      qualification.regraNome ?? "regra"
                    }`}
                  />
                ))}
                {qualifications.length === 0 && (
                  <Chip size="small" variant="outlined" label="Sem regra disparada" />
                )}
              </Stack>
            </Box>
          );
        })}
      </Box>
    </Paper>
  );
}

function PeriodsTable({
  data,
  loading,
}: {
  data?: PeriodoAula[];
  loading: boolean;
}) {
  return (
    <>
      <TableContainer sx={{ maxHeight: 420 }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>Data</TableCell>
              <TableCell>Horario</TableCell>
              <TableCell>Disciplina</TableCell>
              <TableCell>Turma</TableCell>
              <TableCell>Tipo</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.data}</TableCell>
                <TableCell>{item.horaInicio} - {item.horaFim}</TableCell>
                <TableCell>{item.disciplinaNome ?? item.descricao}</TableCell>
                <TableCell>{item.turma ?? "-"}</TableCell>
                <TableCell>{item.tipo}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {!loading && data?.length === 0 && <Empty text="Nenhum periodo de aula encontrado." />}
    </>
  );
}

function DisciplinasTree() {
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Disciplina | null>(null);
  const disciplines = useQuery({
    queryKey: ["catalog", "disciplines", query],
    queryFn: () =>
      apiRequest<Disciplina[]>(
        `/api/catalog/disciplinas?q=${encodeURIComponent(query)}`,
        {},
        session,
      ),
  });
  const periods = useQuery({
    queryKey: ["catalog", "discipline-periods", selected?.id],
    queryFn: () =>
      apiRequest<PeriodoAula[]>(
        `/api/catalog/disciplinas/${selected!.id}/periodos-aula`,
        {},
        session,
      ),
    enabled: Boolean(selected),
  });

  return (
    <Stack spacing={2}>
      <TextField
        label="Buscar disciplina"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="ID, nome ou unidade"
      />
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "360px 1fr" }, gap: 2 }}>
        <Paper variant="outlined" sx={{ maxHeight: 650, overflow: "auto" }}>
          <List dense>
            {disciplines.data?.map((item) => (
              <ListItemButton
                key={item.id}
                selected={selected?.id === item.id}
                onClick={() => setSelected(item)}
              >
                <ListItemText
                  primary={item.nome}
                  secondary={`${item.id} · ${item.unidadeSigla ?? "Sem unidade"}`}
                />
              </ListItemButton>
            ))}
          </List>
          <ErrorAlert error={disciplines.error} />
        </Paper>
        <Paper variant="outlined">
          <Box sx={{ p: 2 }}>
            <Typography variant="h6">
              {selected ? selected.nome : "Selecione uma disciplina"}
            </Typography>
            {selected && <Typography color="text.secondary">ID {selected.id}</Typography>}
          </Box>
          {selected && <PeriodsTable data={periods.data} loading={periods.isLoading} />}
          <ErrorAlert error={periods.error} />
        </Paper>
      </Box>
    </Stack>
  );
}

function PessoasTree() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<PessoaResumo | null>(null);
  const [activityStatus, setActivityStatus] = useState<AtividadeStatus | "ALL">("ALL");
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [userForm, setUserForm] = useState({
    nome: "",
    email: "",
    telefone: "",
    matricula: "",
    password: "",
    roles: [] as string[],
  });
  const [period, setPeriod] = useState(`${new Date().getFullYear()}/${new Date().getMonth() < 6 ? 1 : 2}`);
  const [disciplineDialogOpen, setDisciplineDialogOpen] = useState(false);
  const [disciplineLink, setDisciplineLink] = useState({
    disciplinaId: "",
    turma: "",
    periodoLetivo: period,
    status: "ATIVA",
  });
  const people = useQuery({
    queryKey: ["catalog", "people", query],
    queryFn: () =>
      apiRequest<PessoaResumo[]>(
        `/api/catalog/pessoas?q=${encodeURIComponent(query)}`,
        {},
        session,
      ),
  });
  const disciplines = useQuery({
    queryKey: ["person", selected?.id, "disciplines", period],
    queryFn: () =>
      apiRequest<DisciplinaAluno[]>(
        `/api/pessoas/${selected!.id}/disciplinas?periodoLetivo=${encodeURIComponent(period)}`,
        {},
        session,
      ),
    enabled: Boolean(selected && /^\d{4}\/[12]$/.test(period)),
  });
  const disciplineCatalog = useQuery({
    queryKey: ["catalog", "disciplines", "person-link"],
    queryFn: () => apiRequest<Disciplina[]>("/api/catalog/disciplinas", {}, session),
    enabled: disciplineDialogOpen,
  });
  const linkDiscipline = useMutation({
    mutationFn: () =>
      apiRequest<DisciplinaAluno>(
        `/api/pessoas/${selected!.id}/disciplinas`,
        {
          method: "POST",
          body: JSON.stringify({
            disciplinaId: Number(disciplineLink.disciplinaId),
            turma: disciplineLink.turma,
            periodoLetivo: disciplineLink.periodoLetivo,
            status: disciplineLink.status,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setDisciplineDialogOpen(false);
      setPeriod(disciplineLink.periodoLetivo);
      setDisciplineLink({
        disciplinaId: "",
        turma: "",
        periodoLetivo: disciplineLink.periodoLetivo,
        status: "ATIVA",
      });
      await queryClient.invalidateQueries({
        queryKey: ["person", selected?.id, "disciplines"],
      });
    },
  });
  const activities = useQuery({
    queryKey: ["person", selected?.id, "activities", activityStatus],
    queryFn: () =>
      apiRequest<Atividade[]>(
        `/api/pessoas/${selected!.id}/atividades${
          activityStatus === "ALL" ? "" : `?status=${activityStatus}`
        }`,
        {},
        session,
      ),
    enabled: Boolean(selected),
  });
  const course = useQuery({
    queryKey: ["person", selected?.id, "course"],
    queryFn: () =>
      apiRequest<PessoaCurso>(`/api/pessoas/${selected!.id}/curso`, {}, session),
    enabled: Boolean(selected),
  });
  const personDetails = useQuery({
    queryKey: ["person", selected?.id, "details"],
    queryFn: () => apiRequest<Pessoa>(`/api/pessoas/${selected!.id}`, {}, session),
    enabled: Boolean(selected && hasAnyRole("ADMIN")),
  });
  const updatePerson = useMutation({
    mutationFn: () =>
      apiRequest<Pessoa>(
        `/api/pessoas/${selected!.id}`,
        {
          method: "PUT",
          body: JSON.stringify({
            nome: userForm.nome,
            email: userForm.email,
            userId: selected!.id,
            password: userForm.password || null,
            telefone: userForm.telefone,
            matricula: userForm.matricula,
            roles: userForm.roles,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setUserDialogOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["catalog", "people"] }),
        queryClient.invalidateQueries({ queryKey: ["person", selected?.id, "details"] }),
      ]);
    },
  });

  return (
    <Stack spacing={2}>
      <TextField
        label="Buscar pessoa"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="ID, nome, e-mail ou matricula"
      />
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "340px 1fr" }, gap: 2 }}>
        <Paper variant="outlined" sx={{ maxHeight: 650, overflow: "auto" }}>
          <List dense>
            {people.data?.map((item) => (
              <ListItemButton
                key={item.id}
                selected={selected?.id === item.id}
                onClick={() => setSelected(item)}
              >
                <ListItemText primary={item.nome} secondary={`${item.id} · ${item.email}`} />
              </ListItemButton>
            ))}
          </List>
          <ErrorAlert error={people.error} />
        </Paper>
        <Stack spacing={2}>
          {!selected && <Paper variant="outlined"><Empty text="Selecione uma pessoa." /></Paper>}
          {selected && (
            <>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack direction="row" justifyContent="space-between" spacing={2}>
                  <Typography variant="h6">{selected.nome}</Typography>
                  {hasAnyRole("ADMIN") && (
                    <Button
                      startIcon={<EditOutlined />}
                      onClick={() => {
                        const person = personDetails.data;
                        setUserForm({
                          nome: person?.nome ?? selected.nome,
                          email: person?.email ?? selected.email,
                          telefone: person?.telefone ?? "",
                          matricula: person?.matricula ?? selected.matricula ?? "",
                          password: "",
                          roles: person?.roles ?? selected.roles,
                        });
                        setUserDialogOpen(true);
                      }}
                    >
                      Editar usuário
                    </Button>
                  )}
                </Stack>
                <Typography>{selected.email}</Typography>
                <Typography color="text.secondary">
                  Curso: {course.data?.curso?.nome ?? "Nao vinculado"}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  {selected.roles.map((role) => <Chip key={role} label={role} size="small" />)}
                </Stack>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Stack direction="row" spacing={1.5}>
                    <Typography variant="h6">Disciplinas associadas</Typography>
                    <TextField
                      label="Período letivo"
                      value={period}
                      onChange={(event) => setPeriod(event.target.value)}
                      size="small"
                    />
                  </Stack>
                  <Button
                    variant="contained"
                    startIcon={<AddOutlined />}
                    onClick={() => {
                      setDisciplineLink({ ...disciplineLink, periodoLetivo: period });
                      setDisciplineDialogOpen(true);
                    }}
                  >
                    Atribuir disciplina
                  </Button>
                </Stack>
                <TableContainer sx={{ mt: 2 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Disciplina</TableCell>
                        <TableCell>Turma</TableCell>
                        <TableCell>Período</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {disciplines.data?.map((discipline) => (
                        <TableRow key={discipline.vinculoId}>
                          <TableCell>{discipline.disciplinaNome}</TableCell>
                          <TableCell>{discipline.turma}</TableCell>
                          <TableCell>{discipline.periodoLetivo}</TableCell>
                          <TableCell>
                            <Chip label={discipline.status} size="small" />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                {!disciplines.isLoading && disciplines.data?.length === 0 && (
                  <Empty text="Nenhuma disciplina associada neste período." />
                )}
                <ErrorAlert error={disciplines.error} />
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Typography variant="h6">Atividades e progresso</Typography>
                  <FormControl size="small" sx={{ minWidth: 190 }}>
                    <InputLabel>Status</InputLabel>
                    <Select
                      label="Status"
                      value={activityStatus}
                      onChange={(event) =>
                        setActivityStatus(event.target.value as AtividadeStatus | "ALL")
                      }
                    >
                      <MenuItem value="ALL">Todos os status</MenuItem>
                      {["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA", "EXPIRADA", "CANCELADA"].map(
                        (status) => (
                          <MenuItem key={status} value={status}>{status}</MenuItem>
                        ),
                      )}
                    </Select>
                  </FormControl>
                </Stack>
                <Stack spacing={1.5} sx={{ mt: 2 }}>
                  {activities.data?.map((activity) => (
                    <Box
                      key={activity.id}
                      sx={{
                        p: 2,
                        ml: activity.missaoParentId ? 3 : 0,
                        border: 1,
                        borderColor: "divider",
                        borderLeft: 4,
                        borderLeftColor:
                          activity.status === "CONCLUIDA"
                            ? "success.main"
                            : activity.status === "EM_ANDAMENTO"
                              ? "warning.main"
                              : "grey.400",
                        borderRadius: 1,
                      }}
                    >
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography fontWeight={700}>{activity.missaoTitulo}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {activity.missaoDescricao || activity.missaoTipo}
                          </Typography>
                        </Box>
                        <Chip label={activity.status} size="small" />
                      </Stack>
                      {activity.totalFilhas > 0 && (
                        <Box sx={{ mt: 1.5 }}>
                          <Stack direction="row" justifyContent="space-between">
                            <Typography variant="caption">
                              {activity.filhasConcluidas} de {activity.totalFilhas} etapas concluídas
                            </Typography>
                            <Typography variant="caption">
                              {activity.progressoPercentual}%
                            </Typography>
                          </Stack>
                          <LinearProgress
                            variant="determinate"
                            value={activity.progressoPercentual}
                            sx={{ mt: 0.5, height: 8, borderRadius: 1 }}
                          />
                        </Box>
                      )}
                    </Box>
                  ))}
                  {!activities.isLoading && activities.data?.length === 0 && (
                    <Empty text="Nenhuma atividade encontrada para este filtro." />
                  )}
                </Stack>
                <ErrorAlert error={activities.error} />
              </Paper>
            </>
          )}
        </Stack>
      </Box>
      <Dialog
        open={userDialogOpen}
        onClose={() => setUserDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Editar usuário</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nome"
              value={userForm.nome}
              onChange={(event) => setUserForm({ ...userForm, nome: event.target.value })}
              required
            />
            <TextField
              label="E-mail"
              type="email"
              value={userForm.email}
              onChange={(event) => setUserForm({ ...userForm, email: event.target.value })}
              required
            />
            <TextField
              label="Telefone"
              value={userForm.telefone}
              onChange={(event) => setUserForm({ ...userForm, telefone: event.target.value })}
            />
            <TextField
              label="Matrícula"
              value={userForm.matricula}
              onChange={(event) => setUserForm({ ...userForm, matricula: event.target.value })}
            />
            <TextField
              label="Nova senha"
              type="password"
              value={userForm.password}
              onChange={(event) => setUserForm({ ...userForm, password: event.target.value })}
              helperText="Deixe em branco para manter a senha atual."
            />
            <FormControl>
              <InputLabel>Perfis de acesso</InputLabel>
              <Select
                multiple
                label="Perfis de acesso"
                value={userForm.roles}
                onChange={(event) =>
                  setUserForm({
                    ...userForm,
                    roles: typeof event.target.value === "string"
                      ? event.target.value.split(",")
                      : event.target.value,
                  })
                }
              >
                {["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"].map((role) => (
                  <MenuItem key={role} value={role}>{role}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <ErrorAlert error={updatePerson.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => updatePerson.mutate()}
            disabled={
              updatePerson.isPending
              || !userForm.nome.trim()
              || !userForm.email.trim()
              || userForm.roles.length === 0
            }
          >
            Salvar alterações
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={disciplineDialogOpen}
        onClose={() => setDisciplineDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Atribuir disciplina a {selected?.nome}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormControl required>
              <InputLabel>Disciplina</InputLabel>
              <Select
                label="Disciplina"
                value={disciplineLink.disciplinaId}
                onChange={(event) =>
                  setDisciplineLink({ ...disciplineLink, disciplinaId: event.target.value })
                }
              >
                {disciplineCatalog.data?.map((discipline) => (
                  <MenuItem key={discipline.id} value={String(discipline.id)}>
                    {discipline.nome}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Turma"
              value={disciplineLink.turma}
              onChange={(event) =>
                setDisciplineLink({ ...disciplineLink, turma: event.target.value })
              }
              required
            />
            <TextField
              label="Período letivo"
              value={disciplineLink.periodoLetivo}
              onChange={(event) =>
                setDisciplineLink({ ...disciplineLink, periodoLetivo: event.target.value })
              }
              placeholder="2026/1"
              required
            />
            <FormControl>
              <InputLabel>Status</InputLabel>
              <Select
                label="Status"
                value={disciplineLink.status}
                onChange={(event) =>
                  setDisciplineLink({ ...disciplineLink, status: event.target.value })
                }
              >
                <MenuItem value="ATIVA">Ativa</MenuItem>
                <MenuItem value="CONCLUIDA">Concluída</MenuItem>
                <MenuItem value="CANCELADA">Cancelada</MenuItem>
              </Select>
            </FormControl>
            <ErrorAlert error={linkDiscipline.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDisciplineDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => linkDiscipline.mutate()}
            disabled={
              linkDiscipline.isPending
              || !disciplineLink.disciplinaId
              || !disciplineLink.turma.trim()
              || !/^\d{4}\/[12]$/.test(disciplineLink.periodoLetivo)
            }
          >
            Atribuir
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function CursosTree() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [courseDialogOpen, setCourseDialogOpen] = useState(false);
  const [newCourse, setNewCourse] = useState({ nome: "", unidadeSigla: "" });
  const courses = useQuery({
    queryKey: ["courses", query],
    queryFn: () =>
      apiRequest<Curso[]>(`/api/cursos?q=${encodeURIComponent(query)}`, {}, session),
  });
  const createCourse = useMutation({
    mutationFn: () =>
      apiRequest<Curso>(
        "/api/cursos",
        { method: "POST", body: JSON.stringify(newCourse) },
        session,
      ),
    onSuccess: async () => {
      setCourseDialogOpen(false);
      setNewCourse({ nome: "", unidadeSigla: "" });
      await queryClient.invalidateQueries({ queryKey: ["courses"] });
    },
  });

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
        <TextField
          label="Buscar curso"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="ID, nome ou unidade"
          fullWidth
        />
        {hasAnyRole("ADMIN", "OPERADOR") && (
          <Button
            variant="contained"
            startIcon={<AddOutlined />}
            onClick={() => setCourseDialogOpen(true)}
          >
            Cadastrar curso
          </Button>
        )}
      </Stack>
      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Curso</TableCell>
              <TableCell>Unidade</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {courses.data?.map((course) => (
              <TableRow key={course.id}>
                <TableCell>{course.id}</TableCell>
                <TableCell>{course.nome}</TableCell>
                <TableCell>{course.unidadeSigla ?? "-"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <ErrorAlert error={courses.error} />
      <Dialog
        open={courseDialogOpen}
        onClose={() => setCourseDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Cadastrar curso</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nome"
              value={newCourse.nome}
              onChange={(event) => setNewCourse({ ...newCourse, nome: event.target.value })}
              required
              autoFocus
            />
            <TextField
              label="Sigla da unidade"
              value={newCourse.unidadeSigla}
              onChange={(event) =>
                setNewCourse({ ...newCourse, unidadeSigla: event.target.value })
              }
              placeholder="Ex.: CDTec"
            />
            <ErrorAlert error={createCourse.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCourseDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => createCourse.mutate()}
            disabled={createCourse.isPending || !newCourse.nome.trim()}
          >
            Cadastrar
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export function CatalogPage() {
  const { hasAnyRole } = useAuth();
  const [tab, setTab] = useState(0);
  const managerial = hasAnyRole("ADMIN", "OPERADOR");

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Navegador de dados</Typography>
        <Typography color="text.secondary">
          Explore entidades e relacoes diretas mantendo o contexto do registro selecionado.
        </Typography>
      </Box>
      <Paper>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable">
          <Tab icon={<ApartmentOutlined />} iconPosition="start" label="Compartimentos" />
          <Tab icon={<MenuBookOutlined />} iconPosition="start" label="Disciplinas" />
          <Tab icon={<MenuBookOutlined />} iconPosition="start" label="Cursos" />
          {managerial && (
            <Tab icon={<PersonSearchOutlined />} iconPosition="start" label="Pessoas" />
          )}
        </Tabs>
      </Paper>
      {tab === 0 && <CompartimentosTree />}
      {tab === 1 && <DisciplinasTree />}
      {tab === 2 && <CursosTree />}
      {tab === 3 && managerial && <PessoasTree />}
    </Stack>
  );
}
