import { apiRequest } from "../lib/api";
import type { Session } from "../types/auth";
import type { Compartimento, CompartimentoFilterOptions, Disciplina, PeriodoAula } from "../types/catalog";
import type { PessoaResumo } from "../types/people";
import type { Sensor } from "../types/sensors";

export interface CompartimentoFilters {
  q?: string;
  tipo?: string;
  predio?: string;
  unidade?: string;
  campus?: string;
}

export function listCompartimentos(filters: CompartimentoFilters, session?: Session | null) {
  return apiRequest<Compartimento[]>(
    `/api/catalog/compartimentos?${new URLSearchParams({
      q: filters.q ?? "",
      tipo: filters.tipo ?? "",
      predio: filters.predio ?? "",
      unidade: filters.unidade ?? "",
      campus: filters.campus ?? "",
    }).toString()}`,
    {},
    session,
  );
}

export function getCompartimentoFilterOptions(session?: Session | null) {
  return apiRequest<CompartimentoFilterOptions>(
    "/api/catalog/compartimentos/filter-options",
    {},
    session,
  );
}

export function listCompartimentoSensores(
  compartimentoId: string,
  includeHidden: boolean,
  session?: Session | null,
) {
  return apiRequest<Sensor[]>(
    `/api/catalog/compartimentos/${encodeURIComponent(compartimentoId)}/sensores?includeHidden=${includeHidden}`,
    {},
    session,
  );
}

export function listCompartimentoPeriodos(compartimentoId: string, session?: Session | null) {
  return apiRequest<PeriodoAula[]>(
    `/api/catalog/compartimentos/${encodeURIComponent(compartimentoId)}/periodos-aula`,
    {},
    session,
  );
}

export function listDisciplinas(query: string, session?: Session | null) {
  return apiRequest<Disciplina[]>(
    `/api/catalog/disciplinas?q=${encodeURIComponent(query)}`,
    {},
    session,
  );
}

export function listDisciplinaPeriodos(disciplinaId: number, session?: Session | null) {
  return apiRequest<PeriodoAula[]>(
    `/api/catalog/disciplinas/${disciplinaId}/periodos-aula`,
    {},
    session,
  );
}

export function listPessoasResumo(query: string, session?: Session | null) {
  return apiRequest<PessoaResumo[]>(
    `/api/catalog/pessoas?q=${encodeURIComponent(query)}`,
    {},
    session,
  );
}
