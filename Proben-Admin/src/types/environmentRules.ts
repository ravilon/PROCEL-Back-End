export type EnvironmentRuleScope = "GLOBAL" | "CAMPUS" | "ROOM";
export type EnvironmentRuleCategory =
  "AR_CONDICIONADO" | "ILUMINACAO" | "EQUIPAMENTOS" | string;

export interface EnvironmentRule {
  id: string;
  nome: string;
  descricao?: string | null;
  categoria: EnvironmentRuleCategory;
  ativo: boolean;
  padrao: boolean;
  escopo: EnvironmentRuleScope;
  campusNome?: string | null;
  compartimentoId?: string | null;
  timezone: string;
  cronExpression: string;
  diasSemana: number[];
  vigenciaInicio?: string | null;
  vigenciaFim?: string | null;
  politicaOcupacao: "IGNORE" | "REQUIRE_OCCUPIED" | "REQUIRE_SCHEDULED_USE";
  politicaBeneficiario: "NONE" | "SCHEDULED_STUDENTS" | "CONFIRMED_PRESENCE";
  monitorarSemPontos: boolean;
  pontuacao: Record<string, unknown>;
  configuracao: Record<string, unknown>;
  versao: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export type EnvironmentRuleInput = Omit<
  EnvironmentRule,
  "id" | "versao" | "createdAt" | "createdBy" | "updatedAt" | "updatedBy"
>;

export interface EnvironmentRuleResolution {
  compartimentoId: string;
  categoria: string;
  specific?: EnvironmentRule | null;
  campusDefault?: EnvironmentRule | null;
  globalDefault?: EnvironmentRule | null;
  effective?: EnvironmentRule | null;
  origem?: "SPECIFIC" | "CAMPUS_DEFAULT" | "GLOBAL_DEFAULT" | null;
}

export interface EnvironmentRuleExecution {
  id: string;
  regraId: string;
  regraVersao: number;
  compartimentoId: string;
  categoria: string;
  origem: string;
  tipoExecucao: "SCHEDULED" | "MANUAL";
  instantePrevisto: string;
  instanteReal?: string | null;
  timezone: string;
  status: string;
  resultado?: string | null;
  motivo?: string | null;
  evidencias: Record<string, unknown>;
  contextoAcademico?: Record<string, unknown> | null;
  ocupacao?: Record<string, unknown> | null;
  beneficiarios: unknown[];
  pontosCalculados: number;
  lancamentos: unknown[];
  attempts: number;
  durationMs?: number | null;
  createdAt: string;
}

export interface EnvironmentRuleTestResult {
  resultado: string;
  motivo?: string | null;
  evidencias: Record<string, unknown>;
  contextoAcademico?: Record<string, unknown> | null;
  ocupacao?: Record<string, unknown> | null;
  beneficiarios: unknown[];
  pontosCalculados: number;
}
