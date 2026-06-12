import { PlayArrowOutlined } from "@mui/icons-material";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { ApiError, apiRequest } from "../lib/api";
import type { Role, TipoSensor } from "../types";

type Method = "GET" | "POST" | "PUT" | "DELETE";

interface Field {
  name: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  options?: { value: string; label: string }[];
  lookup?: "sensorTypes";
}

interface Endpoint {
  name: string;
  description: string;
  method: Method;
  path: string;
  roles: Role[];
  pathFields?: Field[];
  queryFields?: Field[];
  body?: string;
}

interface Group {
  name: string;
  endpoints: Endpoint[];
}

const manager: Role[] = ["ADMIN", "OPERADOR"];
const analyst: Role[] = ["ADMIN", "OPERADOR", "ANALISTA"];
const user: Role[] = ["ADMIN", "OPERADOR", "USUARIO"];

const groups: Group[] = [
  {
    name: "Pessoas e disciplinas",
    endpoints: [
      {
        name: "Criar pessoa",
        description: "Cadastro administrativo com roles.",
        method: "POST",
        path: "/api/pessoas",
        roles: ["ADMIN"],
        body: JSON.stringify({ nome: "", email: "", userId: "", password: "", telefone: "", matricula: "", roles: ["USUARIO"] }, null, 2),
      },
      { name: "Buscar pessoa", description: "Busca pelo identificador.", method: "GET", path: "/api/pessoas/{pessoaId}", roles: ["ADMIN", "USUARIO"], pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }] },
      { name: "Atualizar pessoa", description: "Atualiza perfil, senha e roles conforme permissao.", method: "PUT", path: "/api/pessoas/{pessoaId}", roles: ["ADMIN", "USUARIO"], pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }], body: JSON.stringify({ nome: "", email: "", userId: "", password: "", telefone: "", matricula: "", roles: ["USUARIO"] }, null, 2) },
      { name: "Excluir pessoa", description: "Exclusao administrativa.", method: "DELETE", path: "/api/pessoas/{pessoaId}", roles: ["ADMIN"], pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }] },
      { name: "Vincular disciplina", description: "Associa aluno, turma e periodo letivo.", method: "POST", path: "/api/pessoas/{pessoaId}/disciplinas", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }], body: JSON.stringify({ disciplinaId: 0, turma: "T1", periodoLetivo: "2026/1", status: "ATIVA" }, null, 2) },
      { name: "Listar disciplinas do aluno", description: "Consulta por periodo letivo.", method: "GET", path: "/api/pessoas/{pessoaId}/disciplinas", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO"], pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }], queryFields: [{ name: "periodoLetivo", label: "Periodo letivo", placeholder: "2026/1", required: true }] },
      { name: "Atualizar vinculo", description: "Altera status do vinculo.", method: "PUT", path: "/api/pessoas/{pessoaId}/disciplinas/{vinculoId}", roles: manager, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "vinculoId", label: "ID do vinculo", required: true }], body: JSON.stringify({ status: "CONCLUIDA" }, null, 2) },
      { name: "Remover vinculo", description: "Remove disciplina do aluno.", method: "DELETE", path: "/api/pessoas/{pessoaId}/disciplinas/{vinculoId}", roles: manager, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "vinculoId", label: "ID do vinculo", required: true }] },
    ],
  },
  {
    name: "Missoes e atividades",
    endpoints: [
      { name: "Listar missoes", description: "Catalogo de missoes.", method: "GET", path: "/api/missoes", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO"], queryFields: [{ name: "ativo", label: "Ativo", options: [{ value: "true", label: "Sim" }, { value: "false", label: "Nao" }] }] },
      { name: "Criar missao", description: "Cria modelo de missao.", method: "POST", path: "/api/missoes", roles: manager, body: JSON.stringify({ titulo: "", descricao: "", tipo: "Individual", value: 20, ativo: true }, null, 2) },
      { name: "Buscar missao", description: "Consulta por UUID.", method: "GET", path: "/api/missoes/{missaoId}", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO"], pathFields: [{ name: "missaoId", label: "ID da missao", required: true }] },
      { name: "Atualizar missao", description: "Edita modelo.", method: "PUT", path: "/api/missoes/{missaoId}", roles: manager, pathFields: [{ name: "missaoId", label: "ID da missao", required: true }], body: JSON.stringify({ titulo: "", descricao: "", tipo: "Individual", value: 20, ativo: true }, null, 2) },
      { name: "Excluir missao", description: "Remove modelo.", method: "DELETE", path: "/api/missoes/{missaoId}", roles: manager, pathFields: [{ name: "missaoId", label: "ID da missao", required: true }] },
      { name: "Atribuir atividade", description: "Atribui missao para pessoa.", method: "POST", path: "/api/pessoas/{pessoaId}/atividades", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }], body: JSON.stringify({ missaoId: "", status: "PENDENTE", startedAt: null }, null, 2) },
      { name: "Listar atividades", description: "Atividades da pessoa.", method: "GET", path: "/api/pessoas/{pessoaId}/atividades", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }], queryFields: [{ name: "status", label: "Status", options: ["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA", "EXPIRADA", "CANCELADA"].map((value) => ({ value, label: value })) }] },
      { name: "Resumo de atividades", description: "Contagem por status.", method: "GET", path: "/api/pessoas/{pessoaId}/atividades/resumo", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }] },
      { name: "Buscar atividade", description: "Consulta uma atividade por UUID.", method: "GET", path: "/api/pessoas/{pessoaId}/atividades/{atividadeId}", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "atividadeId", label: "ID da atividade", required: true }] },
      { name: "Atualizar atividade", description: "Altera status e datas.", method: "PUT", path: "/api/pessoas/{pessoaId}/atividades/{atividadeId}", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "atividadeId", label: "ID da atividade", required: true }], body: JSON.stringify({ status: "EM_ANDAMENTO", startedAt: null, completedAt: null }, null, 2) },
      { name: "Expirar atividade", description: "Exclusao logica.", method: "DELETE", path: "/api/pessoas/{pessoaId}/atividades/{atividadeId}", roles: user, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "atividadeId", label: "ID da atividade", required: true }] },
    ],
  },
  {
    name: "Cursos",
    endpoints: [
      { name: "Listar cursos", description: "Busca por ID, nome ou unidade.", method: "GET", path: "/api/cursos", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"], queryFields: [{ name: "q", label: "Busca" }] },
      { name: "Buscar curso", description: "Consulta por ID.", method: "GET", path: "/api/cursos/{cursoId}", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"], pathFields: [{ name: "cursoId", label: "ID do curso", required: true }] },
      { name: "Criar curso", description: "Cadastra curso.", method: "POST", path: "/api/cursos", roles: manager, body: JSON.stringify({ nome: "", unidadeSigla: "" }, null, 2) },
      { name: "Atualizar curso", description: "Edita curso.", method: "PUT", path: "/api/cursos/{cursoId}", roles: manager, pathFields: [{ name: "cursoId", label: "ID do curso", required: true }], body: JSON.stringify({ nome: "", unidadeSigla: "" }, null, 2) },
      { name: "Excluir curso", description: "Permitido quando nao existem pessoas vinculadas.", method: "DELETE", path: "/api/cursos/{cursoId}", roles: manager, pathFields: [{ name: "cursoId", label: "ID do curso", required: true }] },
      { name: "Consultar curso da pessoa", description: "Navega da pessoa para seu curso.", method: "GET", path: "/api/pessoas/{pessoaId}/curso", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO"], pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }] },
      { name: "Vincular curso da pessoa", description: "Define ou substitui o curso.", method: "PUT", path: "/api/pessoas/{pessoaId}/curso/{cursoId}", roles: manager, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }, { name: "cursoId", label: "ID do curso", required: true }] },
      { name: "Remover curso da pessoa", description: "Remove o vinculo sem excluir o curso.", method: "DELETE", path: "/api/pessoas/{pessoaId}/curso", roles: manager, pathFields: [{ name: "pessoaId", label: "ID da pessoa", required: true }] },
    ],
  },
  {
    name: "Presencas",
    endpoints: [
      { name: "Check-in", description: "Registra entrada em compartimento.", method: "POST", path: "/api/presencas/checkin", roles: user, body: JSON.stringify({ pessoaId: "", compartimentoId: "", checkinAt: null, source: "console" }, null, 2) },
      { name: "Checkout", description: "Fecha presenca por UUID.", method: "POST", path: "/api/presencas/checkout", roles: user, body: JSON.stringify({ presencaId: "", checkoutAt: null }, null, 2) },
      { name: "Checkout por pessoa", description: "Fecha a presenca aberta.", method: "POST", path: "/api/presencas/checkout/by-pessoa", roles: user, body: JSON.stringify({ pessoaId: "", checkoutAt: null }, null, 2) },
      { name: "Presencas abertas", description: "Lista por compartimento.", method: "GET", path: "/api/presencas/abertas/compartimentos/{compartimentoId}", roles: manager, pathFields: [{ name: "compartimentoId", label: "ID do compartimento", required: true }] },
      { name: "Ocupacao atual", description: "Total de pessoas presentes.", method: "GET", path: "/api/presencas/ocupacao/compartimentos/{compartimentoId}", roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO"], pathFields: [{ name: "compartimentoId", label: "ID do compartimento", required: true }] },
    ],
  },
  {
    name: "Salas e sincronizacao",
    endpoints: [
      { name: "Sincronizar compartimentos", description: "Importa salas da fonte configurada.", method: "POST", path: "/api/rooms/sync", roles: manager },
      { name: "Sincronizar periodos de aula", description: "Inicia job semanal.", method: "POST", path: "/api/rooms/aulas/sync", roles: manager, queryFields: [{ name: "weekStart", label: "Data da semana", placeholder: "2026-06-07", required: true }] },
      { name: "Consultar job de aulas", description: "Estado e resultado do job.", method: "GET", path: "/api/rooms/aulas/sync/{jobId}", roles: analyst, pathFields: [{ name: "jobId", label: "ID do job", required: true }] },
    ],
  },
  {
    name: "Sensores e medicoes",
    endpoints: [
      { name: "Seed de sensores", description: "Carrega arquivo seed.", method: "POST", path: "/api/sensors/seed/from-resource", roles: ["ADMIN"] },
      { name: "Gerar medicoes mock", description: "Executa ingestao simulada.", method: "POST", path: "/api/sensors/ingest/mock", roles: ["ADMIN", "INGESTOR"], body: JSON.stringify({ sensorExternalId: "", minutesBack: 10, everySeconds: 10, source: "console" }, null, 2) },
      { name: "Medicoes por sensor", description: "Consulta historico.", method: "GET", path: "/api/sensors/{sensorExternalId}/medicoes", roles: analyst, pathFields: [{ name: "sensorExternalId", label: "ID externo", required: true }], queryFields: [{ name: "from", label: "De (ISO-8601)" }, { name: "to", label: "Ate (ISO-8601)" }, { name: "limit", label: "Limite", placeholder: "200" }] },
      { name: "Ultima medicao do sensor", description: "Leitura mais recente.", method: "GET", path: "/api/sensors/{sensorExternalId}/medicoes/latest", roles: analyst, pathFields: [{ name: "sensorExternalId", label: "ID externo", required: true }] },
      { name: "Medicoes por compartimento", description: "Historico agregado.", method: "GET", path: "/api/rooms/{compartimentoId}/medicoes", roles: analyst, pathFields: [{ name: "compartimentoId", label: "ID do compartimento", required: true }], queryFields: [{ name: "from", label: "De (ISO-8601)" }, { name: "to", label: "Ate (ISO-8601)" }, { name: "limit", label: "Limite", placeholder: "200" }] },
      { name: "Ultima medicao do compartimento", description: "Leitura mais recente.", method: "GET", path: "/api/rooms/{compartimentoId}/medicoes/latest", roles: analyst, pathFields: [{ name: "compartimentoId", label: "ID do compartimento", required: true }] },
    ],
  },
  {
    name: "Regras de parametros",
    endpoints: [
      { name: "Listar grupos", description: "Grupos de regras.", method: "GET", path: "/api/rules/groups", roles: manager },
      { name: "Criar grupo", description: "Novo grupo de regras.", method: "POST", path: "/api/rules/groups", roles: manager, body: JSON.stringify({ nome: "", descricao: "", ativo: true }, null, 2) },
      { name: "Listar parametros", description: "Definicoes por tipo de sensor.", method: "GET", path: "/api/rules/parameter-defs", roles: manager, queryFields: [{ name: "tipoNome", label: "Tipo do sensor", required: true, lookup: "sensorTypes" }] },
      { name: "Listar regras do grupo", description: "Regras ordenadas por prioridade.", method: "GET", path: "/api/rules/groups/{grupoId}/rules", roles: manager, pathFields: [{ name: "grupoId", label: "ID do grupo", required: true }] },
      { name: "Criar regra", description: "Regra para parametro.", method: "POST", path: "/api/rules/groups/{grupoId}/rules", roles: manager, pathFields: [{ name: "grupoId", label: "ID do grupo", required: true }], body: JSON.stringify({ parametroDefId: "", nome: "", descricao: "", operador: "BETWEEN", valorNumeric1: 20, valorNumeric2: 25, valorText: null, valorBoolean: null, resultado: "ALERTA", severidade: 1, prioridade: 1, ativo: true }, null, 2) },
      { name: "Vincular grupo ao sensor", description: "Agenda ou ativa regras.", method: "POST", path: "/api/rules/sensors/{sensorExternalId}/groups", roles: manager, pathFields: [{ name: "sensorExternalId", label: "ID externo", required: true }], body: JSON.stringify({ grupoRegraId: "", status: "ATIVO", validoDe: null, validoAte: null }, null, 2) },
      { name: "Grupos do sensor", description: "Lista vinculos de regras.", method: "GET", path: "/api/rules/sensors/{sensorExternalId}/groups", roles: manager, pathFields: [{ name: "sensorExternalId", label: "ID externo", required: true }] },
    ],
  },
];

function EndpointForm({ endpoint }: { endpoint: Endpoint }) {
  const { session } = useAuth();
  const [values, setValues] = useState<Record<string, string>>({});
  const [body, setBody] = useState(endpoint.body ?? "");
  const [result, setResult] = useState<unknown>();
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const needsSensorTypes = [...(endpoint.pathFields ?? []), ...(endpoint.queryFields ?? [])]
    .some((field) => field.lookup === "sensorTypes");
  const sensorTypes = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => apiRequest<TipoSensor[]>("/api/sensor-admin/types", {}, session),
    enabled: needsSensorTypes,
  });

  const execute = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setPending(true);
    try {
      let path = endpoint.path;
      endpoint.pathFields?.forEach((field) => {
        path = path.replace(`{${field.name}}`, encodeURIComponent(values[field.name] ?? ""));
      });
      const params = new URLSearchParams();
      endpoint.queryFields?.forEach((field) => {
        const value = values[field.name]?.trim();
        if (value) params.set(field.name, value);
      });
      if ([...params].length) path += `?${params.toString()}`;
      const options: RequestInit = { method: endpoint.method };
      if (endpoint.body) options.body = body;
      setResult(await apiRequest<unknown>(path, options, session));
    } catch (caught) {
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : "Falha desconhecida");
    } finally {
      setPending(false);
    }
  };

  return (
    <Box component="form" onSubmit={execute}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={endpoint.method} color={endpoint.method === "GET" ? "info" : endpoint.method === "DELETE" ? "error" : "primary"} size="small" />
          <Box component="code">{endpoint.path}</Box>
        </Stack>
        {[...(endpoint.pathFields ?? []), ...(endpoint.queryFields ?? [])].map((field) => {
          const options = field.lookup === "sensorTypes"
            ? sensorTypes.data?.map((item) => ({ value: item.nome, label: item.nome })) ?? []
            : field.options;
          return options ? (
            <FormControl key={field.name} size="small" required={field.required}>
              <InputLabel>{field.label}</InputLabel>
              <Select
                label={field.label}
                value={values[field.name] ?? ""}
                onChange={(event) =>
                  setValues((current) => ({ ...current, [field.name]: event.target.value }))
                }
              >
                {!field.required && <MenuItem value="">Todos</MenuItem>}
                {options.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          ) : (
            <TextField
              key={field.name}
              label={field.label}
              required={field.required}
              placeholder={field.placeholder}
              value={values[field.name] ?? ""}
              onChange={(event) =>
                setValues((current) => ({ ...current, [field.name]: event.target.value }))
              }
              size="small"
            />
          );
        })}
        {endpoint.body && (
          <TextField
            label="Body JSON"
            multiline
            minRows={6}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            slotProps={{ input: { sx: { fontFamily: "monospace", fontSize: 13 } } }}
          />
        )}
        {error && <Alert severity="error">{error}</Alert>}
        <Button type="submit" variant="contained" startIcon={<PlayArrowOutlined />} disabled={pending}>
          {pending ? "Executando..." : "Executar"}
        </Button>
        {result !== undefined && (
          <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50", overflow: "auto" }}>
            <Box component="pre" sx={{ m: 0, fontSize: 12 }}>
              {JSON.stringify(result, null, 2)}
            </Box>
          </Paper>
        )}
      </Stack>
    </Box>
  );
}

export function ApiConsolePage() {
  const { session } = useAuth();
  const allowedGroups = useMemo(
    () =>
      groups
        .map((group) => ({
          ...group,
          endpoints: group.endpoints.filter((endpoint) =>
            endpoint.roles.some((role) => session?.roles.includes(role)),
          ),
        }))
        .filter((group) => group.endpoints.length > 0),
    [session],
  );

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Operacoes da API</Typography>
        <Typography color="text.secondary">
          Cadastros, consultas e comandos disponiveis para as roles da sessao.
        </Typography>
      </Box>
      {allowedGroups.map((group) => (
        <Paper key={group.name} variant="outlined">
          <Box sx={{ p: 2 }}>
            <Typography variant="h6">{group.name}</Typography>
          </Box>
          {group.endpoints.map((endpoint) => (
            <Accordion key={`${endpoint.method}-${endpoint.path}`}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box>
                  <Typography>{endpoint.name}</Typography>
                  <Typography variant="body2" color="text.secondary">{endpoint.description}</Typography>
                </Box>
              </AccordionSummary>
              <AccordionDetails>
                <EndpointForm endpoint={endpoint} />
              </AccordionDetails>
            </Accordion>
          ))}
        </Paper>
      ))}
    </Stack>
  );
}
