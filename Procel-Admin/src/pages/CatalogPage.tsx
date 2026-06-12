import {
  ApartmentOutlined,
  AddOutlined,
  MenuBookOutlined,
  PersonSearchOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Button,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
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
  Compartimento,
  Disciplina,
  Curso,
  Medicao,
  PeriodoAula,
  PessoaResumo,
  PessoaCurso,
  Sensor,
  TipoSensor,
} from "../types";

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
  const [selectedRoom, setSelectedRoom] = useState<Compartimento | null>(null);
  const [selectedSensor, setSelectedSensor] = useState<Sensor | null>(null);
  const [sensorDialogOpen, setSensorDialogOpen] = useState(false);
  const [newSensor, setNewSensor] = useState({ externalId: "", nome: "", tipoNome: "" });
  const queryClient = useQueryClient();

  const rooms = useQuery({
    queryKey: ["catalog", "rooms", query],
    queryFn: () =>
      apiRequest<Compartimento[]>(
        `/api/catalog/compartimentos?q=${encodeURIComponent(query)}`,
        {},
        session,
      ),
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
    queryKey: ["measurements", selectedSensor?.externalId],
    queryFn: () =>
      apiRequest<Medicao[]>(
        `/api/sensors/${selectedSensor!.externalId}/medicoes?limit=50`,
        {},
        session,
      ),
    enabled: Boolean(selectedSensor),
  });

  const sensorTypes = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => apiRequest<TipoSensor[]>("/api/sensor-admin/types", {}, session),
    enabled: sensorDialogOpen,
  });

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
      <TextField
        label="Buscar compartimento"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="ID, nome, tipo, predio ou unidade"
        fullWidth
      />
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
                  <List dense>
                    {sensors.data?.map((sensor) => (
                      <ListItemButton
                        key={sensor.externalId}
                        selected={selectedSensor?.externalId === sensor.externalId}
                        onClick={() => setSelectedSensor(sensor)}
                      >
                        <ListItemText
                          primary={sensor.nome}
                          secondary={`${sensor.externalId} · ${sensor.tipoNome}`}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                  {!sensors.isLoading && sensors.data?.length === 0 && (
                    <Empty text="Nenhum sensor vinculado." />
                  )}
                  <ErrorAlert error={sensors.error} />
                </Paper>
              )}

              {selectedSensor && (
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Typography variant="h6">
                    Medicoes de {selectedSensor.nome}
                  </Typography>
                  <ErrorAlert error={measurements.error} />
                  <TableContainer sx={{ maxHeight: 360 }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>Timestamp</TableCell>
                          <TableCell>Origem</TableCell>
                          <TableCell>Valores</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {measurements.data?.map((item) => (
                          <TableRow key={item.id}>
                            <TableCell>{new Date(item.timestamp).toLocaleString()}</TableCell>
                            <TableCell>{item.source}</TableCell>
                            <TableCell>
                              <Box component="code" sx={{ whiteSpace: "pre-wrap" }}>
                                {JSON.stringify(item.valores)}
                              </Box>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                  {!measurements.isLoading && measurements.data?.length === 0 && (
                    <Empty text="Nenhuma medicao encontrada." />
                  )}
                </Paper>
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
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<PessoaResumo | null>(null);
  const [period, setPeriod] = useState(`${new Date().getFullYear()}/${new Date().getMonth() < 6 ? 1 : 2}`);
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
      apiRequest<unknown[]>(
        `/api/pessoas/${selected!.id}/disciplinas?periodoLetivo=${encodeURIComponent(period)}`,
        {},
        session,
      ),
    enabled: Boolean(selected && /^\d{4}\/[12]$/.test(period)),
  });
  const activities = useQuery({
    queryKey: ["person", selected?.id, "activities"],
    queryFn: () =>
      apiRequest<unknown[]>(`/api/pessoas/${selected!.id}/atividades`, {}, session),
    enabled: Boolean(selected),
  });
  const course = useQuery({
    queryKey: ["person", selected?.id, "course"],
    queryFn: () =>
      apiRequest<PessoaCurso>(`/api/pessoas/${selected!.id}/curso`, {}, session),
    enabled: Boolean(selected),
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
                <Typography variant="h6">{selected.nome}</Typography>
                <Typography>{selected.email}</Typography>
                <Typography color="text.secondary">
                  Curso: {course.data?.curso?.nome ?? "Nao vinculado"}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  {selected.roles.map((role) => <Chip key={role} label={role} size="small" />)}
                </Stack>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <TextField
                  label="Periodo letivo"
                  value={period}
                  onChange={(event) => setPeriod(event.target.value)}
                  size="small"
                />
                <Typography variant="h6" sx={{ mt: 2 }}>Disciplinas</Typography>
                <Box component="pre" sx={{ overflow: "auto", fontSize: 12 }}>
                  {JSON.stringify(disciplines.data ?? [], null, 2)}
                </Box>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="h6">Atividades</Typography>
                <Box component="pre" sx={{ overflow: "auto", fontSize: 12 }}>
                  {JSON.stringify(activities.data ?? [], null, 2)}
                </Box>
              </Paper>
            </>
          )}
        </Stack>
      </Box>
    </Stack>
  );
}

function CursosTree() {
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const courses = useQuery({
    queryKey: ["courses", query],
    queryFn: () =>
      apiRequest<Curso[]>(`/api/cursos?q=${encodeURIComponent(query)}`, {}, session),
  });

  return (
    <Stack spacing={2}>
      <TextField
        label="Buscar curso"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="ID, nome ou unidade"
      />
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
