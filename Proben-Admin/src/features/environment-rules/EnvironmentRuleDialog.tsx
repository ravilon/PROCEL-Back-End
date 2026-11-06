import { AddOutlined, DeleteOutlined } from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useId, useMemo, useState } from "react";
import { environmentRulesApi } from "../../api/environmentRules";
import { listCatalogSensors } from "../../api/catalog";
import { listSensorTypes } from "../../api/sensors";
import { useAuth } from "../../auth/AuthContext";
import type { Compartimento } from "../../types/catalog";
import type {
  EnvironmentRule,
  EnvironmentRuleInput,
  EnvironmentRuleScope,
} from "../../types/environmentRules";
import type { ParametroDef, Sensor } from "../../types/sensors";

const categories = ["AR_CONDICIONADO", "ILUMINACAO", "EQUIPAMENTOS"];
const operators = ["GT", "GTE", "LT", "LTE", "EQ", "NEQ", "BETWEEN", "OUTSIDE"];

interface VisualCondition {
  alias: string;
  sensorExternalId: string;
  parametroDefId: string;
  source: "LOCAL" | "SHARED";
  operator: string;
  value: string;
  value2: string;
  maxAgeSeconds: string;
  sevenDayAverage: boolean;
}

interface VisualConfiguration {
  combination: "AND" | "OR";
  preset?: string;
  lowerOffset?: number;
  upperOffset?: number;
  conditions: VisualCondition[];
}

const emptyRule: EnvironmentRuleInput = {
  nome: "",
  descricao: "",
  categoria: "AR_CONDICIONADO",
  ativo: true,
  padrao: false,
  escopo: "ROOM",
  campusNome: null,
  compartimentoId: null,
  timezone: "America/Sao_Paulo",
  cronExpression: "0 */10 * * * *",
  diasSemana: [1, 2, 3, 4, 5, 6, 7],
  vigenciaInicio: null,
  vigenciaFim: null,
  politicaOcupacao: "IGNORE",
  politicaBeneficiario: "NONE",
  monitorarSemPontos: true,
  pontuacao: { satisfied: 0, notSatisfied: 0 },
  configuracao: { combination: "AND", conditions: [] },
};

function asCondition(value: unknown, index: number): VisualCondition {
  const item = (value ?? {}) as Record<string, unknown>;
  return {
    alias: String(item.alias ?? `C${index + 1}`),
    sensorExternalId: String(item.sensorExternalId ?? ""),
    parametroDefId: String(item.parametroDefId ?? ""),
    source: item.source === "SHARED" ? "SHARED" : "LOCAL",
    operator: String(item.operator ?? "EQ"),
    value: item.value == null ? "" : String(item.value),
    value2: item.value2 == null ? "" : String(item.value2),
    maxAgeSeconds: String(item.maxAgeSeconds ?? 600),
    sevenDayAverage: item.sevenDayAverage === true,
  };
}

function initialConfiguration(
  rule: EnvironmentRule | null,
): VisualConfiguration {
  const raw = (rule?.configuracao ?? emptyRule.configuracao) as Record<
    string,
    unknown
  >;
  return {
    combination: raw.combination === "OR" ? "OR" : "AND",
    preset: typeof raw.preset === "string" ? raw.preset : undefined,
    lowerOffset: Number(raw.lowerOffset ?? 3.5),
    upperOffset: Number(raw.upperOffset ?? 3.5),
    conditions: Array.isArray(raw.conditions)
      ? raw.conditions.map(asCondition)
      : [],
  };
}

function serializeCondition(condition: VisualCondition) {
  const result: Record<string, unknown> = {
    alias: condition.alias.trim(),
    parametroDefId: condition.parametroDefId,
    source: condition.source,
    operator: condition.operator,
    maxAgeSeconds: Number(condition.maxAgeSeconds),
  };
  if (condition.sensorExternalId)
    result.sensorExternalId = condition.sensorExternalId;
  if (condition.value !== "")
    result.value = Number.isNaN(Number(condition.value))
      ? condition.value
      : Number(condition.value);
  if (condition.value2 !== "")
    result.value2 = Number.isNaN(Number(condition.value2))
      ? condition.value2
      : Number(condition.value2);
  if (condition.sevenDayAverage) result.sevenDayAverage = true;
  return result;
}

export function EnvironmentRuleDialog({
  open,
  rule,
  rooms,
  campuses,
  existingRules,
  onClose,
  onSaved,
}: {
  open: boolean;
  rule: EnvironmentRule | null;
  rooms: Compartimento[];
  campuses: string[];
  existingRules: EnvironmentRule[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { session } = useAuth();
  const [form, setForm] = useState<EnvironmentRuleInput>(() =>
    rule ? { ...rule } : { ...emptyRule },
  );
  const [configuration, setConfiguration] = useState<VisualConfiguration>(() =>
    initialConfiguration(rule),
  );
  const [building, setBuilding] = useState("");
  const [copyRooms, setCopyRooms] = useState<string[]>([]);
  const sourceRoom = rule?.compartimentoId
    ? rooms.find((room) => room.id === rule.compartimentoId)
    : undefined;
  const [validation, setValidation] = useState<string[]>([]);
  const sensors = useQuery({
    queryKey: ["environment-rules", "sensors"],
    queryFn: () => listCatalogSensors(session),
    enabled: open,
  });
  const sensorTypes = useQuery({
    queryKey: ["environment-rules", "sensor-types"],
    queryFn: () => listSensorTypes(session, false),
    enabled: open,
  });
  const buildings = useMemo(
    () => [...new Set(rooms.map((room) => room.predioNome))].sort(),
    [rooms],
  );
  const visibleRooms = rooms.filter(
    (room) =>
      (!form.campusNome || room.campusNome === form.campusNome) &&
      (!building || room.predioNome === building),
  );
  const declarativeConfiguration = useMemo(
    () => ({
      combination: configuration.combination,
      ...(configuration.preset
        ? {
            preset: configuration.preset,
            lowerOffset: configuration.lowerOffset,
            upperOffset: configuration.upperOffset,
          }
        : {}),
      conditions: configuration.conditions.map(serializeCondition),
    }),
    [configuration],
  );
  const points = form.pontuacao as Record<string, unknown>;

  const validate = () => {
    const errors: string[] = [];
    if (!form.nome.trim()) errors.push("Nome é obrigatório.");
    if (form.escopo === "ROOM" && !form.compartimentoId)
      errors.push("Selecione a sala da regra específica.");
    if (form.escopo === "CAMPUS" && !form.campusNome)
      errors.push("Selecione o campus da regra padrão.");
    if (form.escopo === "GLOBAL" && (form.campusNome || form.compartimentoId))
      errors.push("O escopo GLOBAL não aceita campus ou sala.");
    if (!/^\S+(\s+\S+){5}$/.test(form.cronExpression))
      errors.push("Cron inválido: informe uma expressão de seis campos.");
    try {
      Intl.DateTimeFormat("pt-BR", { timeZone: form.timezone }).format();
    } catch {
      errors.push("Timezone inválido.");
    }
    configuration.conditions.forEach((condition, index) => {
      const label = `Condição ${index + 1}`;
      if (!condition.alias.trim())
        errors.push(`${label}: alias é obrigatório.`);
      if (!condition.sensorExternalId)
        errors.push(`${label}: selecione um sensor.`);
      if (!condition.parametroDefId)
        errors.push(`${label}: selecione um parâmetro compatível.`);
      if (!condition.maxAgeSeconds || Number(condition.maxAgeSeconds) <= 0)
        errors.push(
          `${label}: informe uma validade de medição maior que zero.`,
        );
      if (!configuration.preset && condition.value === "")
        errors.push(`${label}: informe um valor ou referência.`);
      if (
        ["BETWEEN", "OUTSIDE"].includes(condition.operator) &&
        condition.value2 === ""
      )
        errors.push(`${label}: informe o segundo valor.`);
    });
    const conflict = existingRules.some(
      (candidate) =>
        candidate.id !== rule?.id &&
        candidate.ativo &&
        form.ativo &&
        candidate.categoria === form.categoria &&
        candidate.escopo === form.escopo &&
        (form.escopo === "GLOBAL" ||
          candidate.campusNome === form.campusNome) &&
        (form.escopo !== "ROOM" ||
          candidate.compartimentoId === form.compartimentoId),
    );
    if (conflict)
      errors.push(
        "Já existe uma regra ativa conflitante para este escopo e categoria.",
      );
    setValidation(errors);
    return errors.length === 0;
  };

  const save = useMutation({
    mutationFn: async () => {
      if (!validate())
        throw new Error("Revise os campos destacados antes de salvar.");
      const input = { ...form, configuracao: declarativeConfiguration };
      const saved = rule
        ? await environmentRulesApi.update(rule.id, input, session)
        : await environmentRulesApi.create(input, session);
      if (rule?.escopo === "ROOM" && copyRooms.length)
        await environmentRulesApi.copy(rule.id, copyRooms, session);
      return saved;
    },
    onSuccess: onSaved,
  });

  const setScope = (scope: EnvironmentRuleScope) => {
    setBuilding("");
    setForm((current) => ({
      ...current,
      escopo: scope,
      padrao: scope !== "ROOM",
      campusNome: scope === "CAMPUS" ? current.campusNome : null,
      compartimentoId: scope === "ROOM" ? current.compartimentoId : null,
    }));
  };
  const updateCondition = (index: number, patch: Partial<VisualCondition>) =>
    setConfiguration((current) => ({
      ...current,
      conditions: current.conditions.map((condition, position) =>
        position === index ? { ...condition, ...patch } : condition,
      ),
    }));
  const selectSensor = (index: number, sensor: Sensor | undefined) =>
    updateCondition(index, {
      sensorExternalId: sensor?.externalId ?? "",
      parametroDefId: "",
      source: sensor?.contextoCompartilhadoId ? "SHARED" : "LOCAL",
    });
  const parametersFor = (condition: VisualCondition): ParametroDef[] => {
    const sensor = sensors.data?.find(
      (item) => item.externalId === condition.sensorExternalId,
    );
    return (
      sensorTypes.data?.find((type) => type.nome === sensor?.tipoNome)
        ?.parametros ?? []
    );
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        {rule ? "Editar regra de ambiente" : "Criar regra de ambiente"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {(save.error || validation.length > 0) && (
            <Alert severity="error">
              {validation.length ? validation.join(" ") : save.error?.message}
            </Alert>
          )}
          <TextField
            label="Nome"
            value={form.nome}
            required
            onChange={(event) => setForm({ ...form, nome: event.target.value })}
          />
          <TextField
            label="Descrição"
            value={form.descricao ?? ""}
            multiline
            onChange={(event) =>
              setForm({ ...form, descricao: event.target.value })
            }
          />
          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <Choice
              label="Categoria"
              value={form.categoria}
              values={categories}
              onChange={(categoria) => setForm({ ...form, categoria })}
            />
            <Choice
              label="Escopo"
              value={form.escopo}
              values={["ROOM", "CAMPUS", "GLOBAL"]}
              onChange={(scope) => setScope(scope as EnvironmentRuleScope)}
            />
            {form.escopo !== "GLOBAL" && (
              <Choice
                label="Campus"
                value={form.campusNome ?? ""}
                values={campuses}
                onChange={(campusNome) =>
                  setForm({ ...form, campusNome, compartimentoId: null })
                }
              />
            )}
            {form.escopo === "ROOM" && (
              <Choice
                label="Prédio"
                value={building}
                values={buildings}
                onChange={setBuilding}
              />
            )}
            {form.escopo === "ROOM" && (
              <Choice
                label="Sala"
                value={form.compartimentoId ?? ""}
                values={visibleRooms.map((room) => room.id)}
                labels={Object.fromEntries(
                  visibleRooms.map((room) => [room.id, room.nome]),
                )}
                onChange={(compartimentoId) =>
                  setForm({ ...form, compartimentoId })
                }
              />
            )}
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Cron"
              value={form.cronExpression}
              fullWidth
              helperText="Formato de seis campos"
              onChange={(event) =>
                setForm({ ...form, cronExpression: event.target.value })
              }
            />
            <Button
              onClick={() =>
                setForm({ ...form, cronExpression: "0 */10 * * * *" })
              }
            >
              Preset de 10 minutos
            </Button>
            <TextField
              label="Timezone"
              value={form.timezone}
              fullWidth
              onChange={(event) =>
                setForm({ ...form, timezone: event.target.value })
              }
            />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Choice
              label="Ocupação"
              value={form.politicaOcupacao}
              values={["IGNORE", "REQUIRE_OCCUPIED", "REQUIRE_SCHEDULED_USE"]}
              onChange={(value) =>
                setForm({
                  ...form,
                  politicaOcupacao:
                    value as EnvironmentRuleInput["politicaOcupacao"],
                })
              }
            />
            <Choice
              label="Beneficiários"
              value={form.politicaBeneficiario}
              values={["NONE", "SCHEDULED_STUDENTS", "CONFIRMED_PRESENCE"]}
              onChange={(value) =>
                setForm({
                  ...form,
                  politicaBeneficiario:
                    value as EnvironmentRuleInput["politicaBeneficiario"],
                })
              }
            />
            <TextField
              label="Pontos quando atendida"
              type="number"
              value={String(points.satisfied ?? 0)}
              onChange={(event) =>
                setForm({
                  ...form,
                  pontuacao: {
                    ...points,
                    satisfied: Number(event.target.value),
                  },
                })
              }
            />
            <TextField
              label="Pontos quando não atendida"
              type="number"
              value={String(points.notSatisfied ?? 0)}
              onChange={(event) =>
                setForm({
                  ...form,
                  pontuacao: {
                    ...points,
                    notSatisfied: Number(event.target.value),
                  },
                })
              }
            />
          </Stack>
          {form.politicaBeneficiario === "CONFIRMED_PRESENCE" && (
            <Alert severity="info">
              A confirmação usa a fonte de check-in/check-out cadastrada. Sem
              confirmação explícita, nenhum ponto será concedido.
            </Alert>
          )}
          <FormControlLabel
            control={
              <Switch
                checked={form.monitorarSemPontos}
                onChange={(_, checked) =>
                  setForm({ ...form, monitorarSemPontos: checked })
                }
              />
            }
            label="Monitorar sem atribuir pontos"
          />

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack
              direction={{ xs: "column", sm: "row" }}
              spacing={2}
              alignItems={{ sm: "center" }}
            >
              <Typography variant="h6" sx={{ flexGrow: 1 }}>
                Condições
              </Typography>
              <Choice
                label="Combinação"
                value={configuration.combination}
                values={["AND", "OR"]}
                onChange={(combination) =>
                  setConfiguration({
                    ...configuration,
                    combination: combination as "AND" | "OR",
                  })
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={configuration.preset === "ADAPTIVE_COMFORT"}
                    onChange={(_, enabled) =>
                      setConfiguration({
                        ...configuration,
                        preset: enabled ? "ADAPTIVE_COMFORT" : undefined,
                      })
                    }
                  />
                }
                label="Conforto adaptativo"
              />
              <Button
                startIcon={<AddOutlined />}
                onClick={() =>
                  setConfiguration((current) => ({
                    ...current,
                    conditions: [
                      ...current.conditions,
                      asCondition({}, current.conditions.length),
                    ],
                  }))
                }
              >
                Adicionar condição
              </Button>
            </Stack>
            {configuration.preset === "ADAPTIVE_COMFORT" && (
              <Alert severity="info" sx={{ mt: 2 }}>
                Tn = 17,8 + 0,31 × Temed · Liminf = Tn − 3,5 · Limsup = Tn +
                3,5. Os limites são inclusivos.
              </Alert>
            )}
            <Stack spacing={2} sx={{ mt: 2 }}>
              {configuration.conditions.map((condition, index) => (
                <Paper key={index} variant="outlined" sx={{ p: 2 }}>
                  <Stack
                    direction={{ xs: "column", lg: "row" }}
                    spacing={1.5}
                    alignItems={{ lg: "center" }}
                  >
                    <TextField
                      label="Alias"
                      value={condition.alias}
                      onChange={(event) =>
                        updateCondition(index, { alias: event.target.value })
                      }
                      sx={{ minWidth: 100 }}
                    />
                    <Choice
                      label="Sensor"
                      value={condition.sensorExternalId}
                      values={
                        sensors.data?.map((sensor) => sensor.externalId) ?? []
                      }
                      labels={Object.fromEntries(
                        (sensors.data ?? []).map((sensor) => [
                          sensor.externalId,
                          `${sensor.nome} · ${sensor.contextoCompartilhadoNome ? "Compartilhada" : "Local"}`,
                        ]),
                      )}
                      onChange={(externalId) =>
                        selectSensor(
                          index,
                          sensors.data?.find(
                            (sensor) => sensor.externalId === externalId,
                          ),
                        )
                      }
                    />
                    <TextField
                      label="Fonte"
                      value={
                        condition.source === "SHARED"
                          ? "Compartilhada"
                          : "Local"
                      }
                      slotProps={{ input: { readOnly: true } }}
                      sx={{ width: 140 }}
                    />
                    <Choice
                      label="Parâmetro"
                      value={condition.parametroDefId}
                      values={parametersFor(condition).map(
                        (parameter) => parameter.id,
                      )}
                      labels={Object.fromEntries(
                        parametersFor(condition).map((parameter) => [
                          parameter.id,
                          `${parameter.nome}${parameter.numericUnit ? ` (${parameter.numericUnit})` : ""}`,
                        ]),
                      )}
                      onChange={(parametroDefId) =>
                        updateCondition(index, { parametroDefId })
                      }
                    />
                    <Choice
                      label="Operador"
                      value={condition.operator}
                      values={operators}
                      onChange={(operator) =>
                        updateCondition(index, { operator })
                      }
                    />
                    <TextField
                      label="Valor ou referência"
                      value={condition.value}
                      onChange={(event) =>
                        updateCondition(index, { value: event.target.value })
                      }
                    />
                    {["BETWEEN", "OUTSIDE"].includes(condition.operator) && (
                      <TextField
                        label="Segundo valor"
                        value={condition.value2}
                        onChange={(event) =>
                          updateCondition(index, { value2: event.target.value })
                        }
                      />
                    )}
                    <TextField
                      label="Validade (s)"
                      type="number"
                      value={condition.maxAgeSeconds}
                      onChange={(event) =>
                        updateCondition(index, {
                          maxAgeSeconds: event.target.value,
                        })
                      }
                      sx={{ width: 140 }}
                    />
                    {condition.source === "SHARED" && (
                      <FormControlLabel
                        control={
                          <Switch
                            checked={condition.sevenDayAverage}
                            onChange={(_, checked) =>
                              updateCondition(index, {
                                sevenDayAverage: checked,
                              })
                            }
                          />
                        }
                        label="Média 7 dias"
                      />
                    )}
                    <Button
                      color="error"
                      aria-label={`Remover condição ${index + 1}`}
                      onClick={() =>
                        setConfiguration((current) => ({
                          ...current,
                          conditions: current.conditions.filter(
                            (_, position) => position !== index,
                          ),
                        }))
                      }
                    >
                      <DeleteOutlined />
                    </Button>
                  </Stack>
                </Paper>
              ))}
            </Stack>
          </Paper>

          <Box>
            <Typography variant="subtitle2">
              JSON declarativo resultante (somente leitura)
            </Typography>
            <Box
              component="pre"
              aria-label="JSON declarativo resultante"
              sx={{
                p: 2,
                bgcolor: "grey.100",
                overflow: "auto",
                whiteSpace: "pre-wrap",
              }}
            >
              {JSON.stringify(declarativeConfiguration, null, 2)}
            </Box>
          </Box>
          {rule?.escopo === "ROOM" && (
            <FormControl fullWidth>
              <InputLabel id="environment-rule-copy-rooms-label">
                Copiar para salas
              </InputLabel>
              <Select
                id="environment-rule-copy-rooms"
                labelId="environment-rule-copy-rooms-label"
                multiple
                label="Copiar para salas"
                value={copyRooms}
                onChange={(event) =>
                  setCopyRooms(
                    typeof event.target.value === "string"
                      ? event.target.value.split(",")
                      : event.target.value,
                  )
                }
              >
                {rooms
                  .filter(
                    (room) =>
                      room.id !== rule.compartimentoId &&
                      (!sourceRoom ||
                        (room.campusNome === sourceRoom.campusNome &&
                          room.predioId === sourceRoom.predioId)),
                  )
                  .map((room) => (
                    <MenuItem key={room.id} value={room.id}>
                      {room.nome} · {room.campusNome}
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button
          variant="contained"
          disabled={save.isPending}
          onClick={() => save.mutate()}
        >
          Salvar
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function Choice({
  label,
  value,
  values,
  labels,
  onChange,
}: {
  label: string;
  value: string;
  values: string[];
  labels?: Record<string, string>;
  onChange: (value: string) => void;
}) {
  const id = useId();
  return (
    <FormControl sx={{ minWidth: 160 }}>
      <InputLabel id={`${id}-label`}>{label}</InputLabel>
      <Select
        id={id}
        labelId={`${id}-label`}
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <MenuItem value="">
          <em>Selecione</em>
        </MenuItem>
        {values.map((item) => (
          <MenuItem key={item} value={item}>
            {labels?.[item] ?? item}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}
