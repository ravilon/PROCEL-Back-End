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

export interface Sensor {
  externalId: string;
  nome: string;
  tipoNome: string;
  compartimentoId: string;
  compartimentoNome: string;
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
  qualificacoes: Record<string, unknown[]>;
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

export interface ApiErrorBody {
  message?: string;
  error?: string;
}
