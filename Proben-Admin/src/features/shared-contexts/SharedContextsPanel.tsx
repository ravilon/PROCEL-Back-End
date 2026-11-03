import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Link,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import {
  getCompartimentoFilterOptions,
  listCompartimentos,
} from "../../api/catalog";
import { listCatalogSensors } from "../../api/catalog";
import { createSensor, listSensorTypes } from "../../api/sensors";
import {
  createSharedContext,
  deleteSharedContext,
  getSharedContextDeletePreview,
  listSharedContexts,
  listSharedIntegrations,
  replaceContextRooms,
  selectSharedSourceBatch,
  updateSharedContext,
  type ContextDeletePreview,
  type SharedContext,
  type SharedIntegration,
} from "../../api/sharedContexts";
import { useAuth } from "../../auth/AuthContext";
import type {
  Compartimento,
  CompartimentoFilterOptions,
} from "../../types/catalog";
import type { ParametroDef, Sensor, TipoSensor } from "../../types";

export function SharedContextsPanel() {
  const { session } = useAuth();
  const [contexts, setContexts] = useState<SharedContext[]>([]);
  const [integrations, setIntegrations] = useState<
    Record<string, SharedIntegration[]>
  >({});
  const [filters, setFilters] = useState<CompartimentoFilterOptions>();
  const [sensors, setSensors] = useState<Sensor[]>([]);
  const [sensorTypes, setSensorTypes] = useState<TipoSensor[]>([]);
  const [parameters, setParameters] = useState<ParametroDef[]>([]);
  const [search, setSearch] = useState("");
  const [newName, setNewName] = useState("");
  const [error, setError] = useState("");
  const reload = async () => {
    const [result, sensorResult] = await Promise.all([
      listSharedContexts(session),
      listCatalogSensors(session),
    ]);
    setContexts(result);
    setSensors(sensorResult);
    setIntegrations(
      Object.fromEntries(
        await Promise.all(
          result.map(
            async (context) =>
              [
                context.id,
                await listSharedIntegrations(context.id, session),
              ] as const,
          ),
        ),
      ),
    );
  };
  useEffect(() => {
    void Promise.all([
      listSharedContexts(session),
      getCompartimentoFilterOptions(session),
      listCatalogSensors(session),
      listSensorTypes(session),
    ])
      .then(async ([result, options, sensorResult, types]) => {
        setContexts(result);
        setFilters(options);
        setSensors(sensorResult);
        setSensorTypes(types);
        setParameters(
          types
            .flatMap((type) => type.parametros)
            .filter((parameter) => parameter.ativo),
        );
        setIntegrations(
          Object.fromEntries(
            await Promise.all(
              result.map(
                async (context) =>
                  [
                    context.id,
                    await listSharedIntegrations(context.id, session),
                  ] as const,
              ),
            ),
          ),
        );
      })
      .catch((caught) =>
        setError(
          caught instanceof Error
            ? caught.message
            : "Falha ao carregar contextos",
        ),
      );
  }, [session]);
  const visible = useMemo(
    () =>
      contexts.filter((context) =>
        `${context.nome} ${context.descricao ?? ""}`
          .toLocaleLowerCase()
          .includes(search.trim().toLocaleLowerCase()),
      ),
    [contexts, search],
  );
  const create = async () => {
    try {
      await createSharedContext(
        {
          nome: newName.trim(),
          ativo: true,
          locationMode: "OWN",
          timezone: "America/Sao_Paulo",
        },
        session,
      );
      setNewName("");
      await reload();
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Falha ao criar contexto",
      );
    }
  };
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h5">Contextos compartilhados</Typography>
        <Typography color="text.secondary">
          Cobertura de salas e fontes externas compartilhadas.
        </Typography>
      </Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")}>
          {error}
        </Alert>
      )}
      <Card variant="outlined">
        <CardContent>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
            <TextField
              label="Pesquisar contextos"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              fullWidth
            />
            <TextField
              label="Nome do novo contexto"
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
              fullWidth
            />
            <Button
              variant="contained"
              disabled={!newName.trim()}
              onClick={() => void create()}
            >
              Criar
            </Button>
          </Stack>
        </CardContent>
      </Card>
      {visible.map((context) => (
        <ContextCard
          key={`${context.id}:${context.compartimentoIds.join(",")}`}
          context={context}
          integrations={integrations[context.id] ?? []}
          filterOptions={filters}
          sensors={sensors.filter(
            (sensor) => sensor.contextoCompartilhadoId === context.id,
          )}
          sensorTypes={sensorTypes}
          parameters={parameters}
          onChanged={reload}
          onError={setError}
        />
      ))}
    </Stack>
  );
}

function ContextCard({
  context,
  integrations,
  filterOptions,
  sensors,
  sensorTypes,
  parameters,
  onChanged,
  onError,
}: {
  context: SharedContext;
  integrations: SharedIntegration[];
  filterOptions?: CompartimentoFilterOptions;
  sensors: Sensor[];
  sensorTypes: TipoSensor[];
  parameters: ParametroDef[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const { session } = useAuth();
  const [selected, setSelected] = useState(new Set(context.compartimentoIds));
  const [rooms, setRooms] = useState<Compartimento[]>([]);
  const [campus, setCampus] = useState("");
  const [building, setBuilding] = useState("");
  const [roomSearch, setRoomSearch] = useState("");
  const [editOpen, setEditOpen] = useState(false);
  const [name, setName] = useState(context.nome);
  const [description, setDescription] = useState(context.descricao ?? "");
  const [preview, setPreview] = useState<ContextDeletePreview>();
  const [confirmation, setConfirmation] = useState("");
  const [sourceSensor, setSourceSensor] = useState("");
  const [sourceParameter, setSourceParameter] = useState("");
  const [validity, setValidity] = useState(3600);
  const [sensorDialogOpen, setSensorDialogOpen] = useState(false);
  const [sensorExternalId, setSensorExternalId] = useState("");
  const [sensorName, setSensorName] = useState("");
  const [sensorType, setSensorType] = useState("");
  const [creatingSensor, setCreatingSensor] = useState(false);
  useEffect(() => {
    let active = true;
    void listCompartimentos(
      { campus, predio: building, q: roomSearch },
      session,
    )
      .then((result) => {
        if (active) setRooms(result);
      })
      .catch((caught) =>
        onError(
          caught instanceof Error ? caught.message : "Falha ao carregar salas",
        ),
      );
    return () => {
      active = false;
    };
  }, [session, campus, building, roomSearch, onError]);
  const payload = (
    active: boolean,
    newName = context.nome,
    newDescription = context.descricao,
  ) => ({
    nome: newName,
    descricao: newDescription,
    ativo: active,
    locationMode: context.locationMode,
    campusNome: context.campusNome,
    latitude: context.locationMode === "OWN" ? context.latitude : null,
    longitude: context.locationMode === "OWN" ? context.longitude : null,
    timezone: context.timezone,
  });
  const guarded = async (action: () => Promise<unknown>, fallback: string) => {
    try {
      await action();
      await onChanged();
    } catch (caught) {
      onError(caught instanceof Error ? caught.message : fallback);
    }
  };
  const allSelected =
    rooms.length > 0 && rooms.every((room) => selected.has(room.id));
  const someSelected = rooms.some((room) => selected.has(room.id));
  const selectVisible = (checked: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      rooms.forEach((room) => {
        if (checked) next.add(room.id);
        else next.delete(room.id);
      });
      return next;
    });
  const saveSharedSensor = async () => {
    setCreatingSensor(true);
    try {
      const created = await createSensor(
        {
          externalId: sensorExternalId.trim(),
          nome: sensorName.trim(),
          tipoNome: sensorType,
          compartimentoId: null,
          contextoCompartilhadoId: context.id,
        },
        session,
      );
      setSourceSensor(created.externalId);
      setSourceParameter("");
      setSensorDialogOpen(false);
      setSensorExternalId("");
      setSensorName("");
      setSensorType("");
      await onChanged();
    } catch (caught) {
      onError(
        caught instanceof Error
          ? caught.message
          : "Falha ao criar sensor compartilhado",
      );
    } finally {
      setCreatingSensor(false);
    }
  };
  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: "column", md: "row" }}
            justifyContent="space-between"
            gap={1}
          >
            <Box>
              <Typography variant="h6">{context.nome}</Typography>
              <Typography color="text.secondary">
                {context.descricao || "Sem descrição"} ·{" "}
                {context.ativo ? "Ativo" : "Inativo"}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              <Chip label={`${selected.size} salas`} />
              <Button onClick={() => setEditOpen(true)}>Editar</Button>
              <Button
                onClick={() =>
                  void guarded(
                    () =>
                      updateSharedContext(
                        context.id,
                        payload(!context.ativo),
                        session,
                      ),
                    "Falha ao alterar status",
                  )
                }
              >
                {context.ativo ? "Desativar" : "Ativar"}
              </Button>
              <Button
                color="error"
                onClick={() =>
                  void getSharedContextDeletePreview(context.id, session)
                    .then((result) => {
                      setPreview(result);
                      setConfirmation("");
                    })
                    .catch((caught) =>
                      onError(
                        caught instanceof Error
                          ? caught.message
                          : "Falha ao verificar dependências",
                      ),
                    )
                }
              >
                Excluir
              </Button>
            </Stack>
          </Stack>
          <Divider />
          <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
            <TextField
              select
              label="Campus"
              value={campus}
              onChange={(event) => {
                setCampus(event.target.value);
                setBuilding("");
              }}
              sx={{ minWidth: 220 }}
            >
              <MenuItem value="">Todos</MenuItem>
              {filterOptions?.campi.map((value) => (
                <MenuItem key={value} value={value}>
                  {value}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Prédio"
              value={building}
              onChange={(event) => setBuilding(event.target.value)}
              sx={{ minWidth: 220 }}
            >
              <MenuItem value="">Todos</MenuItem>
              {filterOptions?.predios.map((value) => (
                <MenuItem key={value} value={value}>
                  {value}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Buscar sala"
              value={roomSearch}
              onChange={(event) => setRoomSearch(event.target.value)}
              fullWidth
            />
          </Stack>
          <FormControlLabel
            control={
              <Checkbox
                checked={allSelected}
                indeterminate={!allSelected && someSelected}
                onChange={(_, checked) => selectVisible(checked)}
              />
            }
            label={`Selecionar as ${rooms.length} salas filtradas`}
          />
          <Box
            sx={{
              maxHeight: 300,
              overflow: "auto",
              border: 1,
              borderColor: "divider",
              borderRadius: 1,
            }}
          >
            {rooms.map((room) => (
              <FormControlLabel
                key={room.id}
                sx={{
                  display: "flex",
                  m: 0,
                  px: 1,
                  borderBottom: 1,
                  borderColor: "divider",
                }}
                control={
                  <Checkbox
                    checked={selected.has(room.id)}
                    onChange={(_, checked) =>
                      setSelected((current) => {
                        const next = new Set(current);
                        if (checked) next.add(room.id);
                        else next.delete(room.id);
                        return next;
                      })
                    }
                  />
                }
                label={`${room.nome} · ${room.predioNome} · ${room.campusNome}`}
              />
            ))}
            {rooms.length === 0 && (
              <Typography sx={{ p: 2 }} color="text.secondary">
                Nenhuma sala encontrada.
              </Typography>
            )}
          </Box>
          <Stack direction="row" justifyContent="space-between">
            <Button component={RouterLink} to="/integracoes/coletas-http">
              Configurar integração HTTP
            </Button>
            <Button
              variant="contained"
              onClick={() =>
                void guarded(
                  () => replaceContextRooms(context.id, [...selected], session),
                  "Falha ao salvar vínculos",
                )
              }
            >
              Salvar vínculos
            </Button>
          </Stack>
          <Divider />
          <Stack
            direction={{ xs: "column", sm: "row" }}
            justifyContent="space-between"
            alignItems={{ sm: "center" }}
            gap={1}
          >
            <Typography fontWeight={600}>
              Fonte compartilhada das salas selecionadas
            </Typography>
            <Button
              variant="outlined"
              onClick={() => setSensorDialogOpen(true)}
            >
              Criar sensor compartilhado
            </Button>
          </Stack>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
            <TextField
              select
              label="Sensor fonte"
              value={sourceSensor}
              onChange={(event) => {
                setSourceSensor(event.target.value);
                setSourceParameter("");
              }}
              fullWidth
              helperText={
                sensors.length
                  ? "Sensor vinculado a este contexto"
                  : "Crie um sensor compartilhado para continuar"
              }
            >
              {sensors.map((sensor) => (
                <MenuItem key={sensor.externalId} value={sensor.externalId}>
                  {sensor.nome}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Parâmetro"
              value={sourceParameter}
              onChange={(event) => setSourceParameter(event.target.value)}
              fullWidth
            >
              {parameters
                .filter(
                  (parameter) =>
                    parameter.tipoNome ===
                    sensors.find((sensor) => sensor.externalId === sourceSensor)
                      ?.tipoNome,
                )
                .map((parameter) => (
                  <MenuItem key={parameter.id} value={parameter.id}>
                    {parameter.nome}
                  </MenuItem>
                ))}
            </TextField>
            <TextField
              type="number"
              label="Validade (segundos)"
              value={validity}
              onChange={(event) => setValidity(Number(event.target.value))}
            />
            <Button
              variant="outlined"
              disabled={!selected.size || !sourceSensor || !sourceParameter}
              onClick={() =>
                void guarded(
                  () =>
                    selectSharedSourceBatch(
                      {
                        compartimentoIds: [...selected],
                        parametroDefId: sourceParameter,
                        sensorExternalId: sourceSensor,
                        validadeSegundos: validity,
                      },
                      session,
                    ),
                  "Falha ao aplicar fonte",
                )
              }
            >
              Aplicar fonte em lote
            </Button>
          </Stack>
          {integrations.map((integration) => (
            <Alert
              key={integration.id}
              severity={integration.lastError ? "warning" : "info"}
            >
              {integration.nome} · última observação:{" "}
              {integration.lastObservationAt
                ? new Date(integration.lastObservationAt).toLocaleString()
                : "ainda não coletada"}{" "}
              ·{" "}
              <Link component={RouterLink} to="/integracoes/coletas-http">
                abrir
              </Link>
            </Alert>
          ))}
          <Dialog
            open={sensorDialogOpen}
            onClose={() => !creatingSensor && setSensorDialogOpen(false)}
            fullWidth
            maxWidth="sm"
          >
            <DialogTitle>Criar sensor compartilhado</DialogTitle>
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                <Alert severity="info">
                  O sensor será vinculado exclusivamente ao contexto{" "}
                  <strong>{context.nome}</strong>. Suas medições serão
                  persistidas uma única vez e compartilhadas pelas salas que
                  selecionarem esta fonte.
                </Alert>
                <TextField
                  label="ID externo"
                  value={sensorExternalId}
                  onChange={(event) => setSensorExternalId(event.target.value)}
                  helperText="Identificador estável, por exemplo weatherapi-campus-anglo"
                  autoFocus
                />
                <TextField
                  label="Nome"
                  value={sensorName}
                  onChange={(event) => setSensorName(event.target.value)}
                  placeholder="Temperatura externa — Campus Anglo"
                />
                <TextField
                  select
                  label="Tipo de sensor"
                  value={sensorType}
                  onChange={(event) => setSensorType(event.target.value)}
                  helperText="O tipo define os parâmetros disponíveis na coleta"
                >
                  {sensorTypes
                    .filter((type) =>
                      type.parametros.some((parameter) => parameter.ativo),
                    )
                    .map((type) => (
                      <MenuItem key={type.nome} value={type.nome}>
                        {type.nome} ·{" "}
                        {type.parametros
                          .filter((parameter) => parameter.ativo)
                          .map((parameter) => parameter.nome)
                          .join(", ")}
                      </MenuItem>
                    ))}
                </TextField>
                {sensorTypes.every(
                  (type) =>
                    !type.parametros.some((parameter) => parameter.ativo),
                ) && (
                  <Alert severity="warning">
                    Cadastre primeiro um tipo de sensor com ao menos um
                    parâmetro ativo na aba Tipos e parâmetros.
                  </Alert>
                )}
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button
                disabled={creatingSensor}
                onClick={() => setSensorDialogOpen(false)}
              >
                Cancelar
              </Button>
              <Button
                variant="contained"
                disabled={
                  creatingSensor ||
                  !sensorExternalId.trim() ||
                  !sensorName.trim() ||
                  !sensorType
                }
                onClick={() => void saveSharedSensor()}
              >
                {creatingSensor ? "Criando..." : "Criar sensor"}
              </Button>
            </DialogActions>
          </Dialog>
          <Dialog open={editOpen} onClose={() => setEditOpen(false)} fullWidth>
            <DialogTitle>Editar contexto</DialogTitle>
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                <TextField
                  label="Nome"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
                <TextField
                  label="Descrição"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  multiline
                />
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setEditOpen(false)}>Cancelar</Button>
              <Button
                variant="contained"
                disabled={!name.trim()}
                onClick={() =>
                  void guarded(
                    () =>
                      updateSharedContext(
                        context.id,
                        payload(context.ativo, name, description),
                        session,
                      ).then(() => setEditOpen(false)),
                    "Falha ao editar",
                  )
                }
              >
                Salvar
              </Button>
            </DialogActions>
          </Dialog>
          <Dialog
            open={Boolean(preview)}
            onClose={() => setPreview(undefined)}
            fullWidth
          >
            <DialogTitle>Excluir contexto</DialogTitle>
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                <Typography>
                  <strong>{preview?.nome}</strong> ·{" "}
                  {preview?.compartimentos ?? 0} compartimentos
                </Typography>
                <Typography>
                  Sensores: {preview?.sensores ?? 0} · Integrações:{" "}
                  {preview?.integracoes ?? 0} · Fontes:{" "}
                  {preview?.fontesSelecionadas ?? 0}
                </Typography>
                <Typography>
                  Medições: {preview?.medicoes ?? 0} · Auditorias:{" "}
                  {preview?.auditorias ?? 0}
                </Typography>
                {preview && !preview.deletable && (
                  <Alert severity="warning">
                    Exclusão bloqueada: {preview.dependencies.join(", ")}.
                    Desative o contexto para preservar o histórico.
                  </Alert>
                )}
                {preview?.deletable && (
                  <TextField
                    label={`Digite ${preview.nome} para confirmar`}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                  />
                )}
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setPreview(undefined)}>Cancelar</Button>
              {preview && !preview.deletable && context.ativo && (
                <Button
                  onClick={() =>
                    void guarded(
                      () =>
                        updateSharedContext(
                          context.id,
                          payload(false),
                          session,
                        ),
                      "Falha ao desativar",
                    )
                  }
                >
                  Desativar
                </Button>
              )}
              <Button
                color="error"
                disabled={!preview?.deletable || confirmation !== preview.nome}
                onClick={() =>
                  void guarded(
                    () =>
                      deleteSharedContext(context.id, session).then(() =>
                        setPreview(undefined),
                      ),
                    "Falha ao excluir",
                  )
                }
              >
                Excluir definitivamente
              </Button>
            </DialogActions>
          </Dialog>
        </Stack>
      </CardContent>
    </Card>
  );
}
