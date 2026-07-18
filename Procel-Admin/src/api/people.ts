import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type { Atividade, AtividadeStatus } from "../types/missions";
import type { Curso, DisciplinaAluno, Pessoa, PessoaCurso } from "../types/people";
import type { Role } from "../types/auth";

export function listDisciplinasPessoa(
  pessoaId: string,
  periodoLetivo: string,
  session?: Session | null,
) {
  return apiRequest<DisciplinaAluno[]>(
    `/api/pessoas/${encodeURIComponent(pessoaId)}/disciplinas?periodoLetivo=${encodeURIComponent(periodoLetivo)}`,
    {},
    session,
  );
}

export function linkDisciplinaPessoa(
  pessoaId: string,
  payload: { disciplinaId: number; turma: string; periodoLetivo: string; status: string },
  session?: Session | null,
) {
  return apiRequest<DisciplinaAluno>(
    `/api/pessoas/${encodeURIComponent(pessoaId)}/disciplinas`,
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function listAtividadesPessoa(
  pessoaId: string,
  status: AtividadeStatus | "",
  session?: Session | null,
) {
  return apiRequest<Atividade[]>(
    `/api/pessoas/${encodeURIComponent(pessoaId)}/atividades${status ? `?status=${status}` : ""}`,
    {},
    session,
  );
}

export function getPessoaCurso(pessoaId: string, session?: Session | null) {
  return apiRequest<PessoaCurso>(`/api/pessoas/${encodeURIComponent(pessoaId)}/curso`, {}, session);
}

export function getPessoa(pessoaId: string, session?: Session | null) {
  return apiRequest<Pessoa>(`/api/pessoas/${encodeURIComponent(pessoaId)}`, {}, session);
}

export function updatePessoa(
  pessoaId: string,
  payload: {
    nome: string;
    email: string;
    userId: string;
    password?: string;
    telefone?: string;
    matricula?: string;
    roles: Role[];
  },
  session?: Session | null,
) {
  return apiRequest<Pessoa>(
    `/api/pessoas/${encodeURIComponent(pessoaId)}`,
    { method: "PUT", body: JSON.stringify(payload) },
    session,
  );
}

export function createPessoa(
  payload: {
    userId: string;
    nome: string;
    email: string;
    password: string;
    telefone?: string;
    matricula?: string;
    roles: Role[];
  },
  session?: Session | null,
) {
  return apiRequest<Pessoa>(
    "/api/pessoas",
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}

export function deletePessoa(pessoaId: string, session?: Session | null) {
  return apiRequest<void>(
    `/api/pessoas/${encodeURIComponent(pessoaId)}`,
    { method: "DELETE" },
    session,
  );
}

export function listCursos(query: string, session?: Session | null) {
  return apiRequest<Curso[]>(`/api/cursos?q=${encodeURIComponent(query)}`, {}, session);
}

export function createCurso(payload: { nome: string; unidadeSigla?: string }, session?: Session | null) {
  return apiRequest<Curso>(
    "/api/cursos",
    { method: "POST", body: JSON.stringify(payload) },
    session,
  );
}
