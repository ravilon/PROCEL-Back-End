export type Role = "ADMIN" | "OPERADOR" | "ANALISTA" | "USUARIO" | "INGESTOR";

export interface Session {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  userId: string;
  email: string;
  roles: Role[];
}

export interface Pessoa {
  id: string;
  nome: string;
  email: string;
  telefone?: string | null;
  matricula?: string | null;
  createdAt: string;
  roles: Role[];
}

export interface PessoaResumo {
  id: string;
  nome: string;
  email: string;
  matricula?: string | null;
  roles: Role[];
}

export interface Compartimento {
  id: string;
  nome: string;
  tipo: string;
  pavimento?: number | null;
  capacidade?: number | null;
  area?: number | null;
  predioId: string;
  predioNome: string;
  campusNome: string;
  unidadeNome: string;
}

export interface CompartimentoFilterOptions {
  tipos: string[];
  predios: string[];
  unidades: string[];
  campi: string[];
}

export interface Sensor {
  externalId: string;
  nome: string;
  tipoNome: string;
  compartimentoId: string;
  compartimentoNome: string;
  ativo: boolean;
}

export type SensorDataType = "NUMERIC" | "BOOLEAN" | "TEXT";

export interface ParametroDef {
  id: string;
  tipoNome: string;
  nome: string;
  descricao?: string | null;
  dataType: SensorDataType;
  numericUnit?: string | null;
  ativo: boolean;
}

export interface TipoSensor {
  nome: string;
  parametros: ParametroDef[];
}

export interface GrupoRegra {
  id: string;
  nome: string;
  descricao?: string | null;
  ativo: boolean;
  createdAt: string;
}

export interface RegraParametro {
  id: string;
  grupoRegraId: string;
  parametroDefId: string;
  parametroNome: string;
  nome: string;
  descricao?: string | null;
  operador: string;
  valorNumeric1?: number | null;
  valorNumeric2?: number | null;
  valorText?: string | null;
  valorBoolean?: boolean | null;
  resultado: string;
  severidade: number;
  prioridade: number;
  ativo: boolean;
  createdAt: string;
}

export interface Missao {
  id: string;
  titulo: string;
  descricao?: string | null;
  tipo: string;
  value: number;
  ativo: boolean;
  createdAt: string;
  parentId?: string | null;
  parentTitulo?: string | null;
}

export type AtividadeStatus =
  | "PENDENTE"
  | "EM_ANDAMENTO"
  | "CONCLUIDA"
  | "EXPIRADA"
  | "CANCELADA";

export interface Atividade {
  id: string;
  pessoaId: string;
  pessoaNome: string;
  missaoId: string;
  missaoTitulo: string;
  missaoDescricao?: string | null;
  missaoTipo: string;
  missaoValue: number;
  missaoParentId?: string | null;
  status: AtividadeStatus;
  totalFilhas: number;
  filhasConcluidas: number;
  progressoPercentual: number;
  assignedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface Disciplina {
  id: number;
  nome: string;
  unidadeSigla?: string | null;
}

export interface Curso {
  id: number;
  nome: string;
  unidadeSigla?: string | null;
}

export interface PessoaCurso {
  pessoaId: string;
  pessoaNome: string;
  curso?: Curso | null;
}

export interface PeriodoAula {
  id: string;
  compartimentoId: string;
  compartimentoNome: string;
  disciplinaId?: number | null;
  disciplinaNome?: string | null;
  data: string;
  turno: number;
  periodoAula: number;
  horaInicio: string;
  horaFim: string;
  turma?: string | null;
  tipo: string;
  descricao: string;
  source?: string | null;
  sincronizadoEm: string;
}

export interface Medicao {
  id: string;
  sensorExternalId: string;
  tipoNome: string;
  compartimentoId: string;
  timestamp: string;
  receivedAt: string;
  source: string;
  valores: Record<string, unknown>;
  qualificacoes: Record<string, ParametroQualificacao[]>;
}

export type AvaliacaoResultado = "IDEAL" | "NORMAL" | "ALERTA" | "CRITICO" | "INVALIDO";

export interface ParametroQualificacao {
  id: string;
  regraParametroId?: string | null;
  regraNome?: string | null;
  resultado: AvaliacaoResultado;
  severidade: number;
  mensagem?: string | null;
  avaliadoEm: string;
}

export type AlunoDisciplinaStatus = "ATIVA" | "CONCLUIDA" | "CANCELADA";

export interface DisciplinaAluno {
  vinculoId: string;
  pessoaId: string;
  disciplinaId: number;
  disciplinaNome: string;
  unidadeSigla?: string | null;
  turma: string;
  periodoLetivo: string;
  status: AlunoDisciplinaStatus;
  vinculadoEm: string;
}

export interface RoomsSyncResult {
  fetched: number;
  inserted: number;
  updated: number;
  skipped: number;
  elapsedMs: number;
}

export type AulasSyncJobStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export interface AulasSyncProgress {
  roomsTotal: number;
  roomsProcessed: number;
  roomsSynced: number;
  roomsFailed: number;
  progressPercent: number;
}

export interface AulasSyncResult {
  weekStart: string;
  weekEnd: string;
  roomsRequested: number;
  roomsSynced: number;
  roomsFailed: number;
  occurrencesFetched: number;
  occurrencesDeleted: number;
  occurrencesInserted: number;
  disciplinesCreated: number;
  disciplinesUpdated: number;
  elapsedMs: number;
  errors: string[];
}

export interface AulasSyncJob {
  jobId: string;
  status: AulasSyncJobStatus;
  weekStart: string;
  roomId?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  progress: AulasSyncProgress;
  result?: AulasSyncResult | null;
  error?: string | null;
}

export interface ApiErrorBody {
  message?: string;
  error?: string;
}
