import type { Role } from "./auth";

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