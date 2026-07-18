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