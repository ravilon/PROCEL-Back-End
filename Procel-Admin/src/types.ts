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
