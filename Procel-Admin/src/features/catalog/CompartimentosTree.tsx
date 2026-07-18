import {
  AddOutlined,
  ExpandLessOutlined,
  ExpandMoreOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Alert,
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
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  getCompartimentoFilterOptions,
  listCompartimentoPeriodos,
  listCompartimentos,
  listCompartimentoSensores,
} from "../../api/catalog";
import { assignRuleGroupToRooms, listRuleGroups } from "../../api/rules";
import {
  createSensor as createSensorRequest,
  hideSensor as hideSensorRequest,
  listMedicoesByCompartimento,
  listMedicoesBySensor,
  listSensorTypes,
  restoreSensor as restoreSensorRequest,
} from "../../api/sensors";
import type {
  Compartimento,
  Sensor,
} from "../../types";
import { Empty, ErrorAlert, MeasurementCard, PeriodsTable } from "./CatalogShared";
import { measurementStatus, measurementStatusConfig, toDateTimeLocal, type MeasurementStatus } from "./catalogMeasurement";

export function CompartimentosTree() {
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
  const [showHiddenSensors, setShowHiddenSensors] = useState(false);
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
    queryFn: () => listCompartimentos({ q: query, ...roomFilters }, session),
  });
  const roomFilterOptions = useQuery({
    queryKey: ["catalog", "room-filter-options"],
    queryFn: () => getCompartimentoFilterOptions(session),
  });
  const ruleGroups = useQuery({
    queryKey: ["rules", "groups"],
    queryFn: () => listRuleGroups(session),
    enabled: bulkRulesOpen,
  });
  const bulkAssignRules = useMutation({
    mutationFn: () =>
      assignRuleGroupToRooms(
        bulkGroupId,
        {
          compartimentoIds: selectedRoomIds,
          status: bulkStatus,
          validoDe: null,
          validoAte: null,
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
    queryKey: ["catalog", "room-sensors", selectedRoom?.id, showHiddenSensors],
    queryFn: () => listCompartimentoSensores(selectedRoom!.id, showHiddenSensors, session),
    enabled: Boolean(selectedRoom && hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")),
  });

  const periods = useQuery({
    queryKey: ["catalog", "room-periods", selectedRoom?.id],
    queryFn: () => listCompartimentoPeriodos(selectedRoom!.id, session),
    enabled: Boolean(selectedRoom),
  });

  const measurements = useQuery({
    queryKey: [
      "measurements",
      selectedRoom?.id,
      selectedSensor?.externalId,
      measurementFrom,
      measurementTo,
      measurementPage,
    ],
    queryFn: () =>
      selectedSensor
        ? listMedicoesBySensor(
            selectedSensor.externalId,
            { from: measurementFrom, to: measurementTo, page: measurementPage, limit: measurementLimit },
            session,
          )
        : listMedicoesByCompartimento(
            selectedRoom!.id,
            { from: measurementFrom, to: measurementTo, page: measurementPage, limit: measurementLimit },
            session,
          ),
    enabled: Boolean(selectedRoom && measurementFrom && measurementTo),
  });

  const sensorTypes = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => listSensorTypes(session),
    enabled: sensorDialogOpen || Boolean(selectedRoom),
  });
  const visibleSensors = sensors.data?.filter(
    (sensor) => showHiddenSensors || sensor.ativo,
  ) ?? [];

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
      createSensorRequest(
        {
          ...newSensor,
          compartimentoId: selectedRoom!.id,
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
  const hideSensor = useMutation({
    mutationFn: (externalId: string) => hideSensorRequest(externalId, session),
    onMutate: (externalId) => {
      if (selectedSensor?.externalId === externalId) setSelectedSensor(null);
      queryClient.setQueryData<Sensor[]>(
        ["catalog", "room-sensors", selectedRoom?.id, showHiddenSensors],
        (current) =>
          current
            ?.map((sensor) =>
              sensor.externalId === externalId ? { ...sensor, ativo: false } : sensor,
            )
            .filter((sensor) => showHiddenSensors || sensor.ativo),
      );
    },
    onSuccess: async () => {
      setSelectedSensor(null);
      await queryClient.invalidateQueries({
        queryKey: ["catalog", "room-sensors", selectedRoom?.id],
      });
    },
  });
  const restoreSensor = useMutation({
    mutationFn: (externalId: string) => restoreSensorRequest(externalId, session),
    onMutate: (externalId) => {
      queryClient.setQueryData<Sensor[]>(
        ["catalog", "room-sensors", selectedRoom?.id, showHiddenSensors],
        (current) =>
          current?.map((sensor) =>
            sensor.externalId === externalId ? { ...sensor, ativo: true } : sensor,
          ),
      );
    },
    onSuccess: async () => {
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
          {([
            ["tipo", "Tipo", roomFilterOptions.data?.tipos ?? []],
            ["predio", "Predio", roomFilterOptions.data?.predios ?? []],
            ["unidade", "Unidade", roomFilterOptions.data?.unidades ?? []],
            ["campus", "Campus", roomFilterOptions.data?.campi ?? []],
          ] as const).map(([field, label, options]) => (
            <FormControl key={field}>
              <InputLabel>{label}</InputLabel>
              <Select
                label={label}
                value={roomFilters[field]}
                onChange={(event) =>
                  setRoomFilters({ ...roomFilters, [field]: event.target.value })
                }
              >
                <MenuItem value="">Todos</MenuItem>
                {options.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
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
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            <Switch
                              size="small"
                              checked={showHiddenSensors}
                              onChange={(_, checked) => {
                                setShowHiddenSensors(checked);
                                if (!checked && selectedSensor && !selectedSensor.ativo) {
                                  setSelectedSensor(null);
                                }
                              }}
                            />
                            <Typography variant="body2">Mostrar ocultos</Typography>
                          </Stack>
                          <Button
                            size="small"
                            startIcon={<AddOutlined />}
                            onClick={() => setSensorDialogOpen(true)}
                          >
                            Cadastrar
                          </Button>
                        </Stack>
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
                    {visibleSensors.map((sensor) => (
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
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Typography fontWeight={700}>{sensor.nome}</Typography>
                          {!sensor.ativo && <Chip label="Oculto" size="small" />}
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {sensor.externalId}
                        </Typography>
                        <Chip label={sensor.tipoNome} size="small" sx={{ mt: 1 }} />
                        <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                          {sensor.compartimentoNome}
                        </Typography>
                        {hasAnyRole("ADMIN", "OPERADOR") && (
                          <Button
                            size="small"
                            color={sensor.ativo ? "error" : "success"}
                            sx={{ mt: 1 }}
                            onClick={(event) => {
                              event.stopPropagation();
                              if (sensor.ativo) {
                                hideSensor.mutate(sensor.externalId);
                              } else {
                                restoreSensor.mutate(sensor.externalId);
                              }
                            }}
                            disabled={hideSensor.isPending || restoreSensor.isPending}
                          >
                            {sensor.ativo ? "Ocultar" : "Reativar"}
                          </Button>
                        )}
                      </Paper>
                    ))}
                  </Box>
                  {!sensors.isLoading && visibleSensors.length === 0 && (
                    <Empty text="Nenhum sensor vinculado." />
                  )}
                  <ErrorAlert error={sensors.error} />
                </Paper>
              )}

              {selectedRoom && (
                <Stack spacing={2}>
                  {selectedSensor && (
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
                      <Button
                        size="small"
                        onClick={() => {
                          setSelectedSensor(null);
                          setMeasurementPage(0);
                        }}
                      >
                        Ver toda a sala
                      </Button>
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
                  )}

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Box>
                        <Typography variant="h6">Medições</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {selectedSensor
                            ? `Sensor ${selectedSensor.nome}`
                            : `Todos os sensores de ${selectedRoom.nome}`}
                        </Typography>
                      </Box>
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
                        parameters={
                          sensorTypes.data?.find(
                            (type) => type.nome === measurement.tipoNome,
                          )?.parametros ?? []
                        }
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
