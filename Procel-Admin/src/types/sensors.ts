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

export interface SensorGrupoRegra {
  id: string;
  sensorExternalId: string;
  grupoRegraId: string;
  grupoRegraNome: string;
  status: "RASCUNHO" | "AGENDADO" | "ATIVO" | "INATIVO";
  validoDe?: string | null;
  validoAte?: string | null;
  createdAt: string;
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