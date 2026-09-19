import {
  AddOutlined,
  DeleteOutline,
  EditOutlined,
  SaveOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  createMissionCondition,
  createMissionEvent,
  deleteMissionCondition,
  deleteMissionEvent,
  getMissionEvent,
  listMissionEventsForMission,
  updateMissionCondition,
  updateMissionEvent,
} from "../../api/missions";
import { listSensorTypes } from "../../api/sensors";
import { listRuleGroups, listRulesForGroup } from "../../api/rules";
import { useAuth } from "../../auth/AuthContext";
import { ApiError } from "../../lib/api";
import type {
  EventoAgregacao,
  EventoPapel,
  EventoCondicaoFonte,
  AvaliacaoResultado,
  RegraParametro,
  EventoCondicao,
  EventoCondicaoRequest,
  EventoDefinicao,
  EventoDefinicaoRequest,
  EventoModoAvaliacao,
  EventoOperadorLogico,
  EventoPoliticaAtribuicao,
  EventoRegraOperador,
  EventoTipoDisparo,
  Missao,
  SensorDataType,
  TipoSensor,
} from "../../types";

const eventRoles: EventoPapel[] = ["ATRIBUICAO", "PROGRESSO", "CONCLUSAO"];
const conditionSources: EventoCondicaoFonte[] = ["PARAMETRO_VALOR", "AVALIACAO_REGRA"];
const ruleResults: AvaliacaoResultado[] = ["IDEAL", "NORMAL", "ALERTA", "CRITICO", "INVALIDO"];
const eventTypes: EventoTipoDisparo[] = ["MEDICAO_RECEBIDA", "CHECKIN_CONFIRMADO"];
const eventModes: EventoModoAvaliacao[] = ["INSTANTANEO", "DURACAO", "TRANSICAO", "JANELA_ENCERRADA"];
const logicOperators: EventoOperadorLogico[] = ["ALL", "ANY"];
const policies: EventoPoliticaAtribuicao[] = [
  "SEM_ATRIBUICAO_AUTOMATICA",
  "ATIVADOR_DA_MISSAO",
  "ALUNOS_VINCULADOS",
  "ALUNOS_VINCULADOS_COM_OCUPACAO",
];
const aggregations: EventoAgregacao[] = ["ULTIMO", "PRIMEIRO", "MIN", "MAX", "MEDIA", "SOMA", "CONTAGEM", "TEMPO_VERDADEIRO", "DELTA"];
const operators: EventoRegraOperador[] = ["EQ", "NEQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "OUTSIDE", "CONTAINS"];

type EventForm = Omit<EventoDefinicaoRequest, "janelaSegundos" | "duracaoMinimaSegundos" | "quantidadeNecessaria" | "cooldownSegundos" | "ordem" | "lacunaMaximaSegundos"> & {
  janelaSegundos: string;
  duracaoMinimaSegundos: string;
  quantidadeNecessaria: string;
  cooldownSegundos: string;
  lacunaMaximaSegundos: string;
  ordem: string;
};

type ConditionForm = {
  id?: string;
  fonte: EventoCondicaoFonte;
  regraParametroId: string;
  resultadoEsperado: AvaliacaoResultado;
  tipoNome: string;
  parametroDefId: string;
  operador: EventoRegraOperador;
  valorNumeric1: string;
  valorNumeric2: string;
  valorBoolean: string;
  valorText: string;
  agregacao: EventoAgregacao;
  obrigatoria: boolean;
  ordem: string;
  ativo: boolean;
};

const emptyEvent = (): EventForm => ({
  nome: "",
  descricao: "",
  papel: "PROGRESSO",
  tipoDisparo: "MEDICAO_RECEBIDA",
  modoAvaliacao: "INSTANTANEO",
  operadorLogico: "ALL",
  politicaAtribuicao: "SEM_ATRIBUICAO_AUTOMATICA",
  janelaSegundos: "",
  duracaoMinimaSegundos: "",
  quantidadeNecessaria: "1",
  cooldownSegundos: "",
  lacunaMaximaSegundos: "300",
  ordem: "0",
  ativo: true,
});

const emptyCondition = (order: number): ConditionForm => ({
  fonte: "PARAMETRO_VALOR",
  regraParametroId: "",
  resultadoEsperado: "ALERTA",
  tipoNome: "",
  parametroDefId: "",
  operador: "EQ",
  valorNumeric1: "",
  valorNumeric2: "",
  valorBoolean: "",
  valorText: "",
  agregacao: "ULTIMO",
  obrigatoria: true,
  ordem: String(order),
  ativo: true,
});

export function MissionTriggerConfigurator({
  mission,
  readOnly,
  open,
  onClose,
}: {
  mission: Missao;
  readOnly: boolean;
  open: boolean;
  onClose: () => void;
}) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [eventForm, setEventForm] = useState<EventForm>(emptyEvent);
  const [conditionForm, setConditionForm] = useState<ConditionForm | null>(null);
  const [error, setError] = useState<string | null>(null);

  const events = useQuery({
    queryKey: ["missions", "triggers", mission.id],
    queryFn: () => listMissionEventsForMission(mission.id, session),
    enabled: open,
  });
  const detail = useQuery({
    queryKey: ["missions", "trigger", selectedId],
    queryFn: () => getMissionEvent(selectedId ?? "", session),
    enabled: open && Boolean(selectedId),
  });
  const sensorTypes = useQuery({
    queryKey: ["sensor-types", "mission-triggers"],
    queryFn: () => listSensorTypes(session),
    enabled: open,
  });

  const rules = useQuery({
    queryKey: ["sensor-rules", "mission-triggers"],
    queryFn: async () => {
      const groups = await listRuleGroups(session);
      const byGroup = await Promise.all(groups.filter((group) => group.ativo).map((group) => listRulesForGroup(group.id, session)));
      return byGroup.flat().filter((rule) => rule.ativo);
    },
    enabled: open,
  });

  const selected = detail.data;
  const saveEvent = useMutation({
    mutationFn: () => {
      const validation = validateEvent(eventForm);
      if (validation) throw new Error(validation);
      const payload = toEventRequest(eventForm);
      return selectedId
        ? updateMissionEvent(selectedId, payload, session)
        : createMissionEvent(mission.id, payload, session);
    },
    onSuccess: async (saved) => {
      setError(null);
      setSelectedId(saved.id);
      await queryClient.invalidateQueries({ queryKey: ["missions", "triggers", mission.id] });
      await queryClient.invalidateQueries({ queryKey: ["missions", "admin-events"] });
    },
    onError: (cause) => setError(formatError(cause)),
  });
  const deleteEvent = useMutation({
    mutationFn: () => selectedId ? deleteMissionEvent(selectedId, session) : Promise.resolve(),
    onSuccess: async () => {
      setSelectedId(null);
      setEventForm(emptyEvent());
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["missions", "triggers", mission.id] });
      await queryClient.invalidateQueries({ queryKey: ["missions", "admin-events"] });
    },
    onError: (cause) => setError(formatError(cause)),
  });
  const saveCondition = useMutation({
    mutationFn: () => {
      if (!selectedId || !conditionForm) throw new Error("Selecione um evento antes de salvar a condicao.");
      const validation = validateCondition(conditionForm, sensorTypes.data ?? [], rules.data ?? []);
      if (validation) throw new Error(validation);
      const payload = toConditionRequest(conditionForm);
      return conditionForm.id
        ? updateMissionCondition(selectedId, conditionForm.id, payload, session)
        : createMissionCondition(selectedId, payload, session);
    },
    onSuccess: async () => {
      setConditionForm(null);
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["missions", "trigger", selectedId] });
    },
    onError: (cause) => setError(formatError(cause)),
  });
  const deleteCondition = useMutation({
    mutationFn: (conditionId: string) => {
      if (!selectedId) throw new Error("Evento nao selecionado.");
      return deleteMissionCondition(selectedId, conditionId, session);
    },
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["missions", "trigger", selectedId] });
    },
    onError: (cause) => setError(formatError(cause)),
  });

  function selectEvent(event: EventoDefinicao) {
    setSelectedId(event.id);
    setEventForm(fromEvent(event));
    setConditionForm(null);
    setError(null);
  }

  function startNewEvent() {
    setSelectedId(null);
    setEventForm(emptyEvent());
    setConditionForm(null);
    setError(null);
  }

  function editCondition(condition: EventoCondicao) {
    setConditionForm({
      id: condition.id,
      fonte: condition.fonte ?? "PARAMETRO_VALOR",
      regraParametroId: condition.regraParametroId ?? "",
      resultadoEsperado: (condition.resultadoEsperado as AvaliacaoResultado | null) ?? "ALERTA",
      tipoNome: condition.parametroTipoSensor,
      parametroDefId: condition.parametroDefId,
      operador: condition.operador,
      valorNumeric1: condition.valorNumeric1 == null ? "" : String(condition.valorNumeric1),
      valorNumeric2: condition.valorNumeric2 == null ? "" : String(condition.valorNumeric2),
      valorBoolean: condition.valorBoolean == null ? "" : String(condition.valorBoolean),
      valorText: condition.valorText ?? "",
      agregacao: condition.agregacao,
      obrigatoria: condition.obrigatoria,
      ordem: String(condition.ordem ?? 1),
      ativo: condition.ativo,
    });
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xl">
      <DialogTitle>Configurar gatilhos · {mission.titulo}</DialogTitle>
      <DialogContent dividers>
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "280px 1fr" }, gap: 3 }}>
          <Stack spacing={1.5}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle1" fontWeight={700}>Eventos</Typography>
              {!readOnly && <Button size="small" startIcon={<AddOutlined />} onClick={startNewEvent}>Novo</Button>}
            </Stack>
            {events.isLoading && <CircularProgress size={22} />}
            {events.error && <Alert severity="error">{formatError(events.error)}</Alert>}
            {(events.data ?? []).map((event) => (
              <Button
                key={event.id}
                variant={selectedId === event.id ? "contained" : "outlined"}
                color={event.ativo ? "primary" : "inherit"}
                onClick={() => selectEvent(event)}
                sx={{ justifyContent: "space-between", textTransform: "none", textAlign: "left" }}
              >
                <Box>
                  <Typography variant="body2" fontWeight={700}>{event.nome}</Typography>
                  <Typography variant="caption">{event.modoAvaliacao} · ordem {event.ordem ?? 0}</Typography>
                </Box>
                <Chip size="small" label={event.ativo ? "ativo" : "inativo"} />
              </Button>
            ))}
            {!events.isLoading && (events.data ?? []).length === 0 && <Typography color="text.secondary">Nenhum gatilho configurado.</Typography>}
          </Stack>

          <Stack spacing={2}>
            <Typography variant="subtitle1" fontWeight={700}>{selectedId ? "Editar gatilho" : "Novo gatilho"}</Typography>
            <EventFormFields form={eventForm} disabled={readOnly} onChange={setEventForm} />
            <Typography variant="body2" color="text.secondary">
              INSTANTANEO usa o SimpleMissionRuleEngine. DURACAO, TRANSICAO e JANELA_ENCERRADA dependem do fluxo temporal opt-in; a lacuna maxima e configurada globalmente.
            </Typography>
            <Alert severity="info" variant="outlined">
              <Typography variant="body2"><strong>Resumo:</strong> {ruleSummary(eventForm, selected?.condicoes ?? [])}</Typography>
            </Alert>
            {error && <Alert severity="error">{error}</Alert>}
            {!readOnly && (
              <Stack direction="row" spacing={1}>
                <Button variant="contained" startIcon={<SaveOutlined />} onClick={() => saveEvent.mutate()} disabled={saveEvent.isPending}>
                  Salvar gatilho
                </Button>
                {selectedId && <Button color="error" startIcon={<DeleteOutline />} onClick={() => deleteEvent.mutate()} disabled={deleteEvent.isPending}>Desativar</Button>}
              </Stack>
            )}

            {selected && (
              <ConditionSection
                event={selected}
                sensorTypes={sensorTypes.data ?? []}
                rules={rules.data ?? []}
                readOnly={readOnly}
                conditionForm={conditionForm}
                onStart={() => setConditionForm(emptyCondition((selected.condicoes.length ?? 0) + 1))}
                onEdit={editCondition}
                onCancel={() => setConditionForm(null)}
                onChange={setConditionForm}
                onSave={() => saveCondition.mutate()}
                onDelete={(id) => deleteCondition.mutate(id)}
                pending={saveCondition.isPending || deleteCondition.isPending}
              />
            )}
          </Stack>
        </Box>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Fechar</Button></DialogActions>
    </Dialog>
  );
}

function EventFormFields({ form, disabled, onChange }: { form: EventForm; disabled: boolean; onChange: (next: EventForm) => void }) {
  const set = <K extends keyof EventForm>(key: K, value: EventForm[K]) => onChange({ ...form, [key]: value });
  const temporal = form.modoAvaliacao !== "INSTANTANEO";
  return (
    <Stack spacing={2}>
      <TextField label="Nome" value={form.nome} onChange={(e) => set("nome", e.target.value)} required disabled={disabled} />
      <TextField label="Descricao" value={form.descricao ?? ""} onChange={(e) => set("descricao", e.target.value)} multiline minRows={2} disabled={disabled} />
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" }, gap: 2 }}>
        <EnumField label="Papel" value={form.papel ?? "PROGRESSO"} options={eventRoles} disabled={disabled} onChange={(v) => set("papel", v as EventoPapel)} />
        <EnumField label="Tipo de disparo" value={form.tipoDisparo} options={eventTypes} disabled={disabled} onChange={(v) => set("tipoDisparo", v as EventoTipoDisparo)} />
        <EnumField label="Modo de avaliacao" value={form.modoAvaliacao} options={eventModes} disabled={disabled} onChange={(v) => set("modoAvaliacao", v as EventoModoAvaliacao)} />
        <EnumField label="Operador logico" value={form.operadorLogico} options={logicOperators} disabled={disabled} onChange={(v) => set("operadorLogico", v as EventoOperadorLogico)} />
        <EnumField label="Politica de atribuicao" value={form.politicaAtribuicao} options={policies} disabled={disabled} onChange={(v) => set("politicaAtribuicao", v as EventoPoliticaAtribuicao)} />
        <TextField label="Quantidade necessaria" type="number" value={form.quantidadeNecessaria} onChange={(e) => set("quantidadeNecessaria", e.target.value)} inputProps={{ min: 1 }} disabled={disabled} />
        <TextField label="Ordem" type="number" value={form.ordem} onChange={(e) => set("ordem", e.target.value)} inputProps={{ min: 0 }} disabled={disabled} />
        {temporal && <TextField label="Janela (segundos)" type="number" value={form.janelaSegundos} onChange={(e) => set("janelaSegundos", e.target.value)} inputProps={{ min: 0 }} disabled={disabled} />}
        {form.modoAvaliacao === "DURACAO" && <TextField label="Duracao minima (segundos)" type="number" value={form.duracaoMinimaSegundos} onChange={(e) => set("duracaoMinimaSegundos", e.target.value)} inputProps={{ min: 0 }} disabled={disabled} />}
        <TextField label="Lacuna maxima (segundos)" type="number" value={form.lacunaMaximaSegundos} onChange={(e) => set("lacunaMaximaSegundos", e.target.value)} inputProps={{ min: 1 }} disabled={disabled} />
        <TextField label="Cooldown (segundos)" type="number" value={form.cooldownSegundos} onChange={(e) => set("cooldownSegundos", e.target.value)} inputProps={{ min: 0 }} disabled={disabled} />
      </Box>
      <FormControlLabel control={<Switch checked={form.ativo} onChange={(e) => set("ativo", e.target.checked)} disabled={disabled} />} label="Gatilho ativo" />
    </Stack>
  );
}

function ConditionSection({
  event,
  sensorTypes,
  rules,
  readOnly,
  conditionForm,
  onStart,
  onEdit,
  onCancel,
  onChange,
  onSave,
  onDelete,
  pending,
}: {
  event: EventoDefinicao;
  sensorTypes: TipoSensor[];
  rules: RegraParametro[];
  readOnly: boolean;
  conditionForm: ConditionForm | null;
  onStart: () => void;
  onEdit: (condition: EventoCondicao) => void;
  onCancel: () => void;
  onChange: (form: ConditionForm | null) => void;
  onSave: () => void;
  onDelete: (id: string) => void;
  pending: boolean;
}) {
  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1" fontWeight={700}>Condicoes</Typography>
        {!readOnly && <Button size="small" startIcon={<AddOutlined />} onClick={onStart}>Adicionar</Button>}
      </Stack>
      {(event.condicoes ?? []).map((condition) => (
        <Box key={condition.id} sx={{ p: 1.5, border: 1, borderColor: "divider", borderRadius: 1 }}>
          <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography fontWeight={700}>{condition.parametroTipoSensor} · {condition.parametroNome}</Typography>
              <Typography variant="body2">{condition.operador} {conditionValue(condition)} · ordem {condition.ordem ?? 0}</Typography>
            </Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <Chip size="small" color={condition.ativo ? "success" : "default"} label={condition.ativo ? "ativa" : "inativa"} />
              <Chip size="small" label={condition.obrigatoria ? "obrigatoria" : "opcional"} />
              {!readOnly && <Button size="small" startIcon={<EditOutlined />} onClick={() => onEdit(condition)}>Editar</Button>}
              {!readOnly && condition.ativo && <Button size="small" color="error" onClick={() => onDelete(condition.id)}>Excluir</Button>}
            </Stack>
          </Stack>
        </Box>
      ))}
      {(event.condicoes ?? []).filter((condition) => condition.ativo).length === 0 && <Typography color="text.secondary">Nenhuma condicao ativa.</Typography>}
      {conditionForm && !readOnly && (
        <ConditionEditor form={conditionForm} sensorTypes={sensorTypes} rules={rules} pending={pending} onChange={onChange} onSave={onSave} onCancel={onCancel} />
      )}
    </Stack>
  );
}

function ConditionEditor({ form, sensorTypes, rules, pending, onChange, onSave, onCancel }: { form: ConditionForm; sensorTypes: TipoSensor[]; rules: RegraParametro[]; pending: boolean; onChange: (form: ConditionForm) => void; onSave: () => void; onCancel: () => void }) {
  const selectedParameter = useMemo(
    () => sensorTypes.flatMap((type) => type.parametros).find((parameter) => parameter.id === form.parametroDefId),
    [form.parametroDefId, sensorTypes],
  );
  const availableOperators = form.fonte === "AVALIACAO_REGRA" ? ["EQ", "NEQ"] : operators.filter((operator) => supportedOperators(selectedParameter?.dataType).includes(operator));
  const set = <K extends keyof ConditionForm>(key: K, value: ConditionForm[K]) => onChange({ ...form, [key]: value });
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography fontWeight={700}>{form.id ? "Editar condicao" : "Nova condicao"}</Typography>
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" }, gap: 2 }}>
          <EnumField label="Fonte" value={form.fonte} options={conditionSources} onChange={(value) => onChange({ ...form, fonte: value as EventoCondicaoFonte, operador: "EQ", regraParametroId: "" })} />
          <EnumField label="Tipo de sensor" value={form.tipoNome} options={sensorTypes.map((type) => type.nome)} onChange={(value) => onChange({ ...form, tipoNome: value, parametroDefId: "" })} />
          <FormControl required>
            <InputLabel>Parametro</InputLabel>
            <Select label="Parametro" value={form.parametroDefId} onChange={(e) => {
              const parameter = sensorTypes.flatMap((type) => type.parametros).find((item) => item.id === e.target.value);
              onChange({ ...form, parametroDefId: e.target.value, operador: supportedOperators(parameter?.dataType)[0] ?? "EQ" });
            }}>
              {sensorTypes.find((type) => type.nome === form.tipoNome)?.parametros.filter((parameter) => parameter.ativo).map((parameter) => (
                <MenuItem key={parameter.id} value={parameter.id}>{parameter.nome} ({parameter.dataType})</MenuItem>
              ))}
            </Select>
          </FormControl>
          {form.fonte === "AVALIACAO_REGRA" && <FormControl required><InputLabel>Regra de sensor</InputLabel><Select label="Regra de sensor" value={form.regraParametroId} onChange={(e) => set("regraParametroId", e.target.value)}>{rules.filter((rule) => rule.parametroDefId === form.parametroDefId).map((rule) => <MenuItem key={rule.id} value={rule.id}>{rule.nome} ({rule.resultado})</MenuItem>)}</Select></FormControl>}
          {form.fonte === "AVALIACAO_REGRA" && <EnumField label="Resultado esperado" value={form.resultadoEsperado} options={ruleResults} onChange={(value) => set("resultadoEsperado", value as AvaliacaoResultado)} />}
          <EnumField label="Operador" value={form.operador} options={availableOperators} onChange={(value) => set("operador", value as EventoRegraOperador)} />
          <EnumField label="Agregacao" value={form.agregacao} options={aggregations} onChange={(value) => set("agregacao", value as EventoAgregacao)} />
          {form.fonte === "PARAMETRO_VALOR" && selectedParameter?.dataType === "NUMERIC" && <>
            <TextField label="Valor 1" type="number" value={form.valorNumeric1} onChange={(e) => set("valorNumeric1", e.target.value)} required />
            {(form.operador === "BETWEEN" || form.operador === "OUTSIDE") && <TextField label="Valor 2 / limite" type="number" value={form.valorNumeric2} onChange={(e) => set("valorNumeric2", e.target.value)} required />}
          </>}
          {form.fonte === "PARAMETRO_VALOR" && selectedParameter?.dataType === "BOOLEAN" && <FormControl required><InputLabel>Valor</InputLabel><Select label="Valor" value={form.valorBoolean} onChange={(e) => set("valorBoolean", e.target.value)}><MenuItem value="true">true</MenuItem><MenuItem value="false">false</MenuItem></Select></FormControl>}
          {form.fonte === "PARAMETRO_VALOR" && selectedParameter?.dataType === "TEXT" && <TextField label="Texto" value={form.valorText} onChange={(e) => set("valorText", e.target.value)} required />}
          <TextField label="Ordem" type="number" value={form.ordem} onChange={(e) => set("ordem", e.target.value)} inputProps={{ min: 1 }} required />
        </Box>
        <FormControlLabel control={<Checkbox checked={form.obrigatoria} onChange={(e) => set("obrigatoria", e.target.checked)} />} label="Condicao obrigatoria" />
        <FormControlLabel control={<Checkbox checked={form.ativo} onChange={(e) => set("ativo", e.target.checked)} />} label="Condicao ativa" />
        <FormHelperText>BETWEEN e OUTSIDE exigem dois limites numericos. O backend tambem valida o tipo e o operador.</FormHelperText>
        <Stack direction="row" spacing={1}>
          <Button variant="contained" onClick={onSave} disabled={pending}>Salvar condicao</Button>
          <Button onClick={onCancel}>Cancelar</Button>
        </Stack>
      </Stack>
    </Paper>
  );
}

function EnumField({ label, value, options, disabled = false, onChange }: { label: string; value: string; options: string[]; disabled?: boolean; onChange: (value: string) => void }) {
  return <FormControl fullWidth><InputLabel>{label}</InputLabel><Select label={label} value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>{options.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>;
}

function fromEvent(event: EventoDefinicao): EventForm {
  return {
    nome: event.nome,
    descricao: event.descricao ?? "",
    tipoDisparo: event.tipoDisparo,
    modoAvaliacao: event.modoAvaliacao,
    operadorLogico: event.operadorLogico,
    politicaAtribuicao: event.politicaAtribuicao,
    janelaSegundos: numberText(event.janelaSegundos),
    papel: event.papel ?? "PROGRESSO",
    lacunaMaximaSegundos: numberText(event.lacunaMaximaSegundos ?? 300),
    duracaoMinimaSegundos: numberText(event.duracaoMinimaSegundos),
    quantidadeNecessaria: numberText(event.quantidadeNecessaria ?? 1),
    cooldownSegundos: numberText(event.cooldownSegundos),
    ordem: numberText(event.ordem ?? 0),
    ativo: event.ativo,
  };
}

function toEventRequest(form: EventForm): EventoDefinicaoRequest {
  return {
    nome: form.nome.trim(),
    descricao: form.descricao?.trim() || null,
    tipoDisparo: form.tipoDisparo,
    papel: form.papel,
    lacunaMaximaSegundos: optionalNumber(form.lacunaMaximaSegundos),
    modoAvaliacao: form.modoAvaliacao,
    operadorLogico: form.operadorLogico,
    politicaAtribuicao: form.politicaAtribuicao,
    janelaSegundos: optionalNumber(form.janelaSegundos),
    duracaoMinimaSegundos: optionalNumber(form.duracaoMinimaSegundos),
    quantidadeNecessaria: optionalNumber(form.quantidadeNecessaria) ?? 1,
    cooldownSegundos: optionalNumber(form.cooldownSegundos),
    ordem: optionalNumber(form.ordem) ?? 0,
    ativo: form.ativo,
  };
}

function toConditionRequest(form: ConditionForm): EventoCondicaoRequest {
  return {
    parametroDefId: form.parametroDefId,
    operador: form.operador,
    valorNumeric1: form.fonte === "AVALIACAO_REGRA" || form.valorNumeric1 === "" ? null : Number(form.valorNumeric1),
    valorNumeric2: form.fonte === "AVALIACAO_REGRA" || form.valorNumeric2 === "" ? null : Number(form.valorNumeric2),
    valorBoolean: form.fonte === "AVALIACAO_REGRA" || form.valorBoolean === "" ? null : form.valorBoolean === "true",
    valorText: form.fonte === "AVALIACAO_REGRA" ? null : form.valorText.trim() || null,
    agregacao: form.agregacao,
    obrigatoria: form.obrigatoria,
    ordem: Number(form.ordem),
    fonte: form.fonte,
    regraParametroId: form.fonte === "AVALIACAO_REGRA" ? form.regraParametroId : null,
    resultadoEsperado: form.fonte === "AVALIACAO_REGRA" ? form.resultadoEsperado : null,
    ativo: form.ativo,
  };
}

function validateEvent(form: EventForm): string | null {
  if (!form.nome.trim()) return "Nome do gatilho e obrigatorio.";
  for (const [label, value] of [["Janela", form.janelaSegundos], ["Duracao minima", form.duracaoMinimaSegundos], ["Cooldown", form.cooldownSegundos], ["Quantidade", form.quantidadeNecessaria], ["Ordem", form.ordem], ["Lacuna maxima", form.lacunaMaximaSegundos]] as const) {
    if (value !== "" && (!Number.isInteger(Number(value)) || Number(value) < 0)) return `${label} deve ser um inteiro nao negativo.`;
  }
  if (form.lacunaMaximaSegundos !== "" && Number(form.lacunaMaximaSegundos) < 1) return "Lacuna maxima deve ser maior que zero.";
  if (Number(form.quantidadeNecessaria || 1) < 1) return "Quantidade necessaria deve ser maior que zero.";
  if (form.modoAvaliacao === "DURACAO" && (!form.janelaSegundos || !form.duracaoMinimaSegundos)) return "DURACAO exige janela e duracao minima.";
  return null;
}

function validateCondition(form: ConditionForm, sensorTypes: TipoSensor[], rules: RegraParametro[]): string | null {
  const parameter = sensorTypes.flatMap((type) => type.parametros).find((item) => item.id === form.parametroDefId);
  if (!parameter) return "Selecione um parametro ativo.";
  if (!form.ordem || !Number.isInteger(Number(form.ordem)) || Number(form.ordem) < 1) return "Ordem deve ser um inteiro maior ou igual a 1.";
  if (form.fonte === "AVALIACAO_REGRA") {
    if (!rules.some((rule) => rule.id === form.regraParametroId && rule.parametroDefId === form.parametroDefId)) return "Selecione uma regra ativa deste parametro.";
    if (!["EQ", "NEQ"].includes(form.operador)) return "Resultado de regra aceita apenas EQ ou NEQ.";
    return null;
  }
  if (!supportedOperators(parameter.dataType).includes(form.operador)) return "Operador incompatível com o tipo do parametro.";
  if (parameter.dataType === "NUMERIC" && (form.valorNumeric1 === "" || ((form.operador === "BETWEEN" || form.operador === "OUTSIDE") && form.valorNumeric2 === ""))) return "Informe os valores numericos exigidos.";
  if (parameter.dataType === "NUMERIC" && ["BETWEEN", "OUTSIDE"].includes(form.operador) && Number(form.valorNumeric2) < Number(form.valorNumeric1)) return "O limite final deve ser maior ou igual ao inicial.";
  if (parameter.dataType === "BOOLEAN" && form.valorBoolean === "") return "Informe o valor booleano.";
  if (parameter.dataType === "TEXT" && !form.valorText.trim()) return "Informe o texto da condicao.";
  return null;
}

function supportedOperators(dataType?: SensorDataType): EventoRegraOperador[] {
  if (dataType === "BOOLEAN") return ["EQ", "NEQ"];
  if (dataType === "TEXT") return ["EQ", "NEQ", "CONTAINS"];
  if (dataType === "NUMERIC") return ["EQ", "NEQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "OUTSIDE"];
  return ["EQ", "NEQ"];
}

function conditionValue(condition: EventoCondicao) {
  if (condition.fonte === "AVALIACAO_REGRA") return condition.resultadoEsperado ?? "-";
  if (condition.valorBoolean != null) return String(condition.valorBoolean);
  if (condition.valorText != null) return condition.valorText;
  if (condition.valorNumeric2 != null) return `${condition.valorNumeric1} .. ${condition.valorNumeric2}`;
  return condition.valorNumeric1 == null ? "-" : String(condition.valorNumeric1);
}

function ruleSummary(form: EventForm, conditions: EventoCondicao[]) {
  const activeConditions = conditions.filter((condition) => condition.ativo);
  const conditionSummary = activeConditions.length === 0
    ? "sem condicoes"
    : conditions
      .slice()
      .sort((left, right) => (left.ordem ?? 0) - (right.ordem ?? 0))
      .map((condition) => `${condition.fonte === "AVALIACAO_REGRA" ? condition.regraNome ?? condition.parametroNome : condition.parametroNome} ${condition.operador} ${conditionValue(condition)}`)
      .join(` ${form.operadorLogico} `);
  const temporal = form.modoAvaliacao === "INSTANTANEO"
    ? "avaliacao instantanea"
    : `${form.modoAvaliacao.toLowerCase()} em janela de ${form.janelaSegundos || "-"}s`;
  return `${form.nome.trim() || "Gatilho sem nome"}: ${form.papel ?? "PROGRESSO"}, ${form.tipoDisparo}, ${temporal}, ${conditionSummary}.`;
}

function optionalNumber(value: string | number | null | undefined) {
  if (value === "" || value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function numberText(value: number | null | undefined) {
  return value == null ? "" : String(value);
}

function formatError(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : "Falha ao processar a operacao.";
}
