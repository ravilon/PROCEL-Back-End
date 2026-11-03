import {
  AddOutlined,
  ContentCopyOutlined,
  DeleteOutlined,
  EditOutlined,
  PlayArrowOutlined,
  ScienceOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { environmentRulesApi } from "../../api/environmentRules";
import { listCompartimentos } from "../../api/catalog";
import { useAuth } from "../../auth/AuthContext";
import type {
  EnvironmentRule,
  EnvironmentRuleInput,
} from "../../types/environmentRules";

const categories = ["AR_CONDICIONADO", "ILUMINACAO", "EQUIPAMENTOS"];
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
  configuracao: { conditions: [] },
};

export function EnvironmentRulesPanel() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = hasAnyRole("ADMIN");
  const [filters, setFilters] = useState({
    campus: "",
    room: "",
    category: "",
    scope: "",
    active: "",
  });
  const [editing, setEditing] = useState<EnvironmentRule | null>(null);
  const [creating, setCreating] = useState(false);
  const [resolutionRoom, setResolutionRoom] = useState("");
  const [historyRule, setHistoryRule] = useState<EnvironmentRule | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const rooms = useQuery({
    queryKey: ["environment-rules", "rooms"],
    queryFn: () => listCompartimentos({}, session),
  });
  const campuses = useMemo(
    () => [...new Set(rooms.data?.map((room) => room.campusNome) ?? [])].sort(),
    [rooms.data],
  );
  const rules = useQuery({
    queryKey: ["environment-rules", filters],
    queryFn: () => {
      const query = new URLSearchParams();
      Object.entries(filters).forEach(
        ([key, value]) => value && query.set(key, value),
      );
      return environmentRulesApi.list(query, session);
    },
  });
  const resolution = useQuery({
    queryKey: ["environment-rules", "resolution", resolutionRoom],
    queryFn: () => environmentRulesApi.resolve(resolutionRoom, session),
    enabled: Boolean(resolutionRoom),
  });
  const history = useQuery({
    queryKey: ["environment-rules", "history", historyRule?.id],
    queryFn: () => environmentRulesApi.executions(historyRule!.id, session),
    enabled: Boolean(historyRule),
  });
  const nextRuns = useQuery({
    queryKey: ["environment-rules", "next-runs", historyRule?.id],
    queryFn: () => environmentRulesApi.nextRuns(historyRule!.id, session),
    enabled: Boolean(historyRule),
  });
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["environment-rules"] });
  const active = useMutation({
    mutationFn: ({ rule, value }: { rule: EnvironmentRule; value: boolean }) =>
      environmentRulesApi.setActive(rule.id, value, session),
    onSuccess: refresh,
  });
  const duplicate = useMutation({
    mutationFn: (id: string) => environmentRulesApi.duplicate(id, session),
    onSuccess: refresh,
  });
  const run = useMutation<
    unknown,
    Error,
    { rule: EnvironmentRule; dry: boolean }
  >({
    mutationFn: ({ rule, dry }: { rule: EnvironmentRule; dry: boolean }) => {
      const room = rule.compartimentoId ?? resolutionRoom;
      if (!room) throw new Error("Selecione uma sala para esta operação.");
      return dry
        ? environmentRulesApi.test(rule.id, room, session)
        : environmentRulesApi.execute(rule.id, room, session);
    },
    onSuccess: (result, variables) => {
      setMessage(
        variables.dry
          ? `Teste: ${JSON.stringify(result)}`
          : "Execução manual enfileirada.",
      );
      refresh();
    },
  });
  const remove = useMutation({
    mutationFn: async (rule: EnvironmentRule) => {
      const preview = await environmentRulesApi.deletePreview(rule.id, session);
      if (!preview.deletable)
        throw new Error(
          `Exclusão bloqueada: ${preview.executionCount} execução(ões). Desative a regra.`,
        );
      if (!window.confirm(`Excluir definitivamente “${rule.nome}”?`)) return;
      await environmentRulesApi.remove(rule.id, session);
    },
    onSuccess: refresh,
  });

  const error =
    rules.error ?? active.error ?? duplicate.error ?? run.error ?? remove.error;
  return (
    <Stack spacing={2}>
      {message && <Alert onClose={() => setMessage(null)}>{message}</Alert>}
      {error && <Alert severity="error">{error.message}</Alert>}
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack
          direction={{ xs: "column", lg: "row" }}
          spacing={1.5}
          alignItems={{ lg: "center" }}
        >
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Regras de Ambiente
          </Typography>
          <Filter
            label="Campus"
            value={filters.campus}
            values={campuses}
            onChange={(campus) => setFilters({ ...filters, campus })}
          />
          <Filter
            label="Sala"
            value={filters.room}
            values={rooms.data?.map((r) => r.id) ?? []}
            onChange={(room) => setFilters({ ...filters, room })}
          />
          <Filter
            label="Categoria"
            value={filters.category}
            values={categories}
            onChange={(category) => setFilters({ ...filters, category })}
          />
          <Filter
            label="Escopo"
            value={filters.scope}
            values={["ROOM", "CAMPUS", "GLOBAL"]}
            onChange={(scope) => setFilters({ ...filters, scope })}
          />
          <Filter
            label="Estado"
            value={filters.active}
            values={["true", "false"]}
            onChange={(activeValue) =>
              setFilters({ ...filters, active: activeValue })
            }
          />
          {isAdmin && (
            <Button
              variant="contained"
              startIcon={<AddOutlined />}
              onClick={() => setCreating(true)}
            >
              Nova regra
            </Button>
          )}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Regra</TableCell>
              <TableCell>Categoria</TableCell>
              <TableCell>Aplicação</TableCell>
              <TableCell>Agenda</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell align="right">Ações</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rules.data?.map((rule) => (
              <TableRow
                key={rule.id}
                hover
                onClick={() => setHistoryRule(rule)}
                sx={{ cursor: "pointer" }}
              >
                <TableCell>
                  <Typography fontWeight={600}>{rule.nome}</Typography>
                  <Typography variant="caption">v{rule.versao}</Typography>
                </TableCell>
                <TableCell>{rule.categoria}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={rule.escopo === "ROOM" ? "primary" : "default"}
                    label={
                      rule.escopo === "ROOM"
                        ? `Específica · ${rule.compartimentoId}`
                        : `Fallback ${rule.escopo} · ${rule.campusNome ?? "todos"}`
                    }
                  />
                </TableCell>
                <TableCell>
                  {rule.cronExpression}
                  <br />
                  <Typography variant="caption">{rule.timezone}</Typography>
                </TableCell>
                <TableCell>
                  <Switch
                    size="small"
                    checked={rule.ativo}
                    disabled={!isAdmin}
                    onClick={(event) => event.stopPropagation()}
                    onChange={(_, value) => active.mutate({ rule, value })}
                  />
                </TableCell>
                <TableCell
                  align="right"
                  onClick={(event) => event.stopPropagation()}
                >
                  <IconButton
                    aria-label="Testar regra"
                    onClick={() => run.mutate({ rule, dry: true })}
                  >
                    <ScienceOutlined />
                  </IconButton>
                  {isAdmin && (
                    <IconButton
                      aria-label="Executar manualmente"
                      onClick={() => run.mutate({ rule, dry: false })}
                    >
                      <PlayArrowOutlined />
                    </IconButton>
                  )}
                  {isAdmin && (
                    <IconButton
                      aria-label="Editar regra"
                      onClick={() => setEditing(rule)}
                    >
                      <EditOutlined />
                    </IconButton>
                  )}
                  {isAdmin && (
                    <IconButton
                      aria-label="Duplicar regra"
                      onClick={() => duplicate.mutate(rule.id)}
                    >
                      <ContentCopyOutlined />
                    </IconButton>
                  )}
                  {isAdmin && (
                    <IconButton
                      aria-label="Excluir regra"
                      onClick={() => remove.mutate(rule)}
                    >
                      <DeleteOutlined />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={2}
          alignItems={{ md: "center" }}
        >
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6">Prévia da precedência</Typography>
            <Typography color="text.secondary">
              Regra específica → padrão do campus → padrão global
            </Typography>
          </Box>
          <FormControl sx={{ minWidth: 260 }}>
            <InputLabel>Sala</InputLabel>
            <Select
              label="Sala"
              value={resolutionRoom}
              onChange={(event) => setResolutionRoom(event.target.value)}
            >
              {rooms.data?.map((room) => (
                <MenuItem key={room.id} value={room.id}>
                  {room.nome} · {room.campusNome}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={1}
          sx={{ mt: 2 }}
        >
          {resolution.data?.map((item) => (
            <Paper
              key={item.categoria}
              variant="outlined"
              sx={{ p: 1.5, flex: 1 }}
            >
              <Typography fontWeight={600}>{item.categoria}</Typography>
              <Chip
                sx={{ mt: 1 }}
                color={item.effective ? "success" : "default"}
                label={
                  item.effective
                    ? `${item.origem}: ${item.effective.nome}`
                    : "Sem regra aplicável"
                }
              />
              <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                Específica: {item.specific?.nome ?? "—"}
                <br />
                Campus: {item.campusDefault?.nome ?? "—"}
                <br />
                Global: {item.globalDefault?.nome ?? "—"}
              </Typography>
            </Paper>
          ))}
        </Stack>
      </Paper>

      <RuleDialog
        key={editing?.id ?? (creating ? "new" : "closed")}
        open={creating || Boolean(editing)}
        rule={editing}
        rooms={rooms.data ?? []}
        campuses={campuses}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        onSaved={() => {
          setCreating(false);
          setEditing(null);
          refresh();
        }}
      />
      <HistoryDialog
        rule={historyRule}
        executions={history.data ?? []}
        nextRuns={nextRuns.data ?? []}
        onClose={() => setHistoryRule(null)}
      />
    </Stack>
  );
}

function Filter({
  label,
  value,
  values,
  onChange,
}: {
  label: string;
  value: string;
  values: string[];
  onChange: (value: string) => void;
}) {
  return (
    <FormControl size="small" sx={{ minWidth: 130 }}>
      <InputLabel>{label}</InputLabel>
      <Select
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <MenuItem value="">Todos</MenuItem>
        {values.map((item) => (
          <MenuItem key={item} value={item}>
            {item === "true" ? "Ativa" : item === "false" ? "Inativa" : item}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}

function RuleDialog({
  open,
  rule,
  rooms,
  campuses,
  onClose,
  onSaved,
}: {
  open: boolean;
  rule: EnvironmentRule | null;
  rooms: Awaited<ReturnType<typeof listCompartimentos>>;
  campuses: string[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { session } = useAuth();
  const initial = rule ? { ...rule } : { ...emptyRule };
  const [form, setForm] = useState<EnvironmentRuleInput>(initial);
  const [configuration, setConfiguration] = useState(
    JSON.stringify(initial.configuracao, null, 2),
  );
  const [points, setPoints] = useState(
    JSON.stringify(initial.pontuacao, null, 2),
  );
  const [copyRooms, setCopyRooms] = useState("");
  const save = useMutation({
    mutationFn: async () => {
      const input = {
        ...form,
        configuracao: JSON.parse(configuration),
        pontuacao: JSON.parse(points),
      };
      const saved = rule
        ? await environmentRulesApi.update(rule.id, input, session)
        : await environmentRulesApi.create(input, session);
      if (rule && copyRooms.trim())
        await environmentRulesApi.copy(
          rule.id,
          copyRooms
            .split(",")
            .map((v) => v.trim())
            .filter(Boolean),
          session,
        );
      return saved;
    },
    onSuccess: onSaved,
  });
  const setScope = (scope: EnvironmentRuleInput["escopo"]) =>
    setForm({
      ...form,
      escopo: scope,
      padrao: scope !== "ROOM",
      campusNome: scope === "CAMPUS" ? form.campusNome : null,
      compartimentoId: scope === "ROOM" ? form.compartimentoId : null,
    });
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {rule ? "Editar regra de ambiente" : "Criar regra de ambiente"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {save.error && <Alert severity="error">{save.error.message}</Alert>}
          <TextField
            label="Nome"
            value={form.nome}
            onChange={(e) => setForm({ ...form, nome: e.target.value })}
            required
          />
          <TextField
            label="Descrição"
            value={form.descricao ?? ""}
            onChange={(e) => setForm({ ...form, descricao: e.target.value })}
            multiline
          />
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Filter
              label="Categoria"
              value={form.categoria}
              values={categories}
              onChange={(categoria) => setForm({ ...form, categoria })}
            />
            <Filter
              label="Escopo"
              value={form.escopo}
              values={["ROOM", "CAMPUS", "GLOBAL"]}
              onChange={(scope) =>
                setScope(scope as EnvironmentRuleInput["escopo"])
              }
            />
            {form.escopo === "ROOM" && (
              <Filter
                label="Sala"
                value={form.compartimentoId ?? ""}
                values={rooms.map((r) => r.id)}
                onChange={(compartimentoId) =>
                  setForm({ ...form, compartimentoId })
                }
              />
            )}
            {form.escopo === "CAMPUS" && (
              <Filter
                label="Campus"
                value={form.campusNome ?? ""}
                values={campuses}
                onChange={(campusNome) => setForm({ ...form, campusNome })}
              />
            )}
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Cron"
              value={form.cronExpression}
              onChange={(e) =>
                setForm({ ...form, cronExpression: e.target.value })
              }
              fullWidth
              helperText="Preset 10 min: 0 */10 * * * *"
            />
            <TextField
              label="Timezone"
              value={form.timezone}
              onChange={(e) => setForm({ ...form, timezone: e.target.value })}
              fullWidth
            />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Filter
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
            <Filter
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
          </Stack>
          <Stack direction="row" alignItems="center">
            <Switch
              checked={form.monitorarSemPontos}
              onChange={(_, monitorarSemPontos) =>
                setForm({ ...form, monitorarSemPontos })
              }
            />
            <Typography>Monitorar sem atribuir pontos</Typography>
          </Stack>
          <TextField
            label="Configuração declarativa"
            value={configuration}
            onChange={(e) => setConfiguration(e.target.value)}
            multiline
            minRows={8}
            helperText='Condições: alias, parametroDefId, source LOCAL/SHARED, operator, value, maxAgeSeconds; preset "ADAPTIVE_COMFORT".'
          />
          <TextField
            label="Pontuação por resultado"
            value={points}
            onChange={(e) => setPoints(e.target.value)}
            multiline
            minRows={3}
          />
          {rule?.escopo === "ROOM" && (
            <TextField
              label="Copiar para salas (IDs separados por vírgula)"
              value={copyRooms}
              onChange={(e) => setCopyRooms(e.target.value)}
              helperText="Cria instâncias independentes."
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button
          variant="contained"
          onClick={() => save.mutate()}
          disabled={save.isPending}
        >
          Salvar
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function HistoryDialog({
  rule,
  executions,
  nextRuns,
  onClose,
}: {
  rule: EnvironmentRule | null;
  executions: Array<{
    id: string;
    tipoExecucao: string;
    instantePrevisto: string;
    resultado?: string | null;
    motivo?: string | null;
    evidencias: unknown;
    attempts: number;
    pontosCalculados: number;
  }>;
  nextRuns: string[];
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<unknown>(null);
  return (
    <Dialog open={Boolean(rule)} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>Histórico · {rule?.nome}</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          Próximos horários:{" "}
          {nextRuns.length
            ? nextRuns
                .map((value) => new Date(value).toLocaleString())
                .join(" · ")
            : "nenhum na vigência"}
        </Alert>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Prevista</TableCell>
              <TableCell>Tipo</TableCell>
              <TableCell>Resultado</TableCell>
              <TableCell>Motivo</TableCell>
              <TableCell>Tentativas</TableCell>
              <TableCell>Pontos</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {executions.map((execution) => (
              <TableRow
                key={execution.id}
                hover
                onClick={() => setDetail(execution)}
                sx={{ cursor: "pointer" }}
              >
                <TableCell>
                  {new Date(execution.instantePrevisto).toLocaleString()}
                </TableCell>
                <TableCell>{execution.tipoExecucao}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={execution.resultado ?? "PENDENTE"}
                  />
                </TableCell>
                <TableCell>{execution.motivo ?? "—"}</TableCell>
                <TableCell>{execution.attempts}</TableCell>
                <TableCell>{execution.pontosCalculados}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {detail != null && (
          <Box
            component="pre"
            sx={{
              mt: 2,
              p: 2,
              bgcolor: "grey.100",
              overflow: "auto",
              whiteSpace: "pre-wrap",
            }}
          >
            {JSON.stringify(detail, null, 2)}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Fechar</Button>
      </DialogActions>
    </Dialog>
  );
}
