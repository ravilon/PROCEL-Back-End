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

export interface Disciplina {
  id: number;
  nome: string;
  unidadeSigla?: string | null;
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