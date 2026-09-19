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

export type EventoPapel = "ATRIBUICAO" | "PROGRESSO" | "CONCLUSAO";
export type EventoCondicaoFonte = "PARAMETRO_VALOR" | "AVALIACAO_REGRA";
export type EventoTipoDisparo = "MEDICAO_RECEBIDA" | "CHECKIN_CONFIRMADO";
export type EventoModoAvaliacao = "INSTANTANEO" | "DURACAO" | "TRANSICAO" | "JANELA_ENCERRADA";
export type EventoOperadorLogico = "ALL" | "ANY";
export type EventoPoliticaAtribuicao =
  | "SEM_ATRIBUICAO_AUTOMATICA"
  | "ATIVADOR_DA_MISSAO"
  | "ALUNOS_VINCULADOS"
  | "ALUNOS_VINCULADOS_COM_OCUPACAO";
export type EventoAgregacao = "ULTIMO" | "PRIMEIRO" | "MIN" | "MAX" | "MEDIA" | "SOMA" | "CONTAGEM" | "TEMPO_VERDADEIRO" | "DELTA";
export type EventoRegraOperador =
  | "EQ"
  | "NEQ"
  | "GT"
  | "GTE"
  | "LT"
  | "LTE"
  | "BETWEEN"
  | "OUTSIDE"
  | "CONTAINS";
export type EventoAvaliacaoRequestStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "RETRY";
export type EventoJanelaAvaliacaoStatus =
  | "ABERTA"
  | "PROCESSING"
  | "SATISFEITA"
  | "INVALIDADA"
  | "EXPIRADA"
  | "FAILED";
export type EventoOcorrenciaStatus = "DETECTADO" | "CONFIRMADO" | "DESCARTADO" | "PROCESSADO";
export type EventoJanelaEvidenciaPapel = "INICIO" | "MANUTENCAO" | "FIM" | "INTERRUPCAO";
export type EventoOcorrenciaEvidenciaPapel = "CONDICAO" | "ANTES" | "DEPOIS" | "JANELA";

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EventoCondicao {
  fonte?: EventoCondicaoFonte;
  regraParametroId?: string | null;
  regraNome?: string | null;
  resultadoEsperado?: string | null;
  id: string;
  eventoDefinicaoId: string;
  parametroDefId: string;
  parametroNome: string;
  parametroTipoSensor: string;
  parametroDataType: "NUMERIC" | "BOOLEAN" | "TEXT";
  operador: EventoRegraOperador;
  valorNumeric1?: number | null;
  valorNumeric2?: number | null;
  valorBoolean?: boolean | null;
  valorText?: string | null;
  agregacao: EventoAgregacao;
  obrigatoria: boolean;
  ordem?: number | null;
  ativo: boolean;
  createdAt: string;
}

export interface EventoDefinicao {
  id: string;
  missaoId: string;
  missaoTitulo: string;
  nome: string;
  descricao?: string | null;
  tipoDisparo: EventoTipoDisparo;
  modoAvaliacao: EventoModoAvaliacao;
  operadorLogico: EventoOperadorLogico;
  politicaAtribuicao: EventoPoliticaAtribuicao;
  janelaSegundos?: number | null;
  duracaoMinimaSegundos?: number | null;
  quantidadeNecessaria?: number | null;
  cooldownSegundos?: number | null;
  ordem?: number | null;
  ativo: boolean;
  createdAt: string;
  updatedAt?: string | null;
  condicoes: EventoCondicao[];
  papel?: EventoPapel;
  lacunaMaximaSegundos?: number | null;
}

export interface EventoDefinicaoRequest {
  papel?: EventoPapel;
  lacunaMaximaSegundos?: number | null;
  nome: string;
  descricao?: string | null;
  tipoDisparo: EventoTipoDisparo;
  modoAvaliacao: EventoModoAvaliacao;
  operadorLogico: EventoOperadorLogico;
  politicaAtribuicao: EventoPoliticaAtribuicao;
  janelaSegundos?: number | null;
  duracaoMinimaSegundos?: number | null;
  quantidadeNecessaria?: number | null;
  cooldownSegundos?: number | null;
  ordem?: number | null;
  ativo: boolean;
}

export interface EventoCondicaoRequest {
  fonte?: EventoCondicaoFonte;
  regraParametroId?: string | null;
  resultadoEsperado?: string | null;
  parametroDefId: string;
  operador: EventoRegraOperador;
  valorNumeric1?: number | null;
  valorNumeric2?: number | null;
  valorBoolean?: boolean | null;
  valorText?: string | null;
  agregacao: EventoAgregacao;
  obrigatoria: boolean;
  ordem: number;
}

export interface EventDefinitionSummary {
  id: string;
  missaoId: string;
  missaoTitulo: string;
  nome: string;
  tipoDisparo: EventoTipoDisparo;
  modoAvaliacao: EventoModoAvaliacao;
  ativo: boolean;
  ordem?: number | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface EvaluationRequest {
  id: string;
  medicaoId: string;
  status: EventoAvaliacaoRequestStatus;
  attempts: number;
  availableAt?: string | null;
  claimedAt?: string | null;
  leaseUntil?: string | null;
  processedAt?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface MissionWindow {
  id: string;
  eventoDefinicaoId: string;
  eventoNome: string;
  compartimentoId: string;
  periodoAulaId?: string | null;
  status: EventoJanelaAvaliacaoStatus;
  inicioEm: string;
  fimPrevistoEm?: string | null;
  ultimaMedicaoEm?: string | null;
  proximaAvaliacaoEm?: string | null;
  leaseUntil?: string | null;
  attempts: number;
  chaveIdempotencia: string;
  contextoSnapshot?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface WindowEvidence {
  id: string;
  janelaId: string;
  medicaoId: string;
  parametroValorId?: string | null;
  papel: EventoJanelaEvidenciaPapel;
  createdAt: string;
}

export interface MissionOccurrence {
  id: string;
  eventoDefinicaoId: string;
  eventoNome: string;
  compartimentoId?: string | null;
  periodoAulaId?: string | null;
  sensorExternalId?: string | null;
  status: EventoOcorrenciaStatus;
  inicioEm?: string | null;
  fimEm?: string | null;
  detectadoEm: string;
  chaveIdempotencia: string;
  contextoSnapshot?: string | null;
  conteudoFingerprint?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface OccurrenceEvidence {
  id: string;
  ocorrenciaId: string;
  medicaoId: string;
  parametroValorId?: string | null;
  papel: EventoOcorrenciaEvidenciaPapel;
  createdAt: string;
}

export interface WorkerState {
  enabled: boolean;
  fixedDelay: string;
  batchSize: number;
  leaseDuration: string;
  maxAttempts: number;
  backlog: number;
}

export interface DroolsState {
  selectedRuleEngine: string;
  temporalDroolsEnabled: boolean;
  temporalActivitiesEnabled: boolean;
  maxFactsPerEvaluation: number;
  maxCacheEntries: number;
  cacheExpiration: string;
  evaluationTimeout: string;
  maximumSampleGap: string;
}

export interface MissionWorkerStatus {
  evaluation: WorkerState;
  temporalWindows: WorkerState;
  drools: DroolsState;
}

export interface WorkerRunResponse {
  worker: string;
  processed: number;
}

export interface MissionEventFilters {
  missionId?: string;
  active?: boolean | "";
  tipoDisparo?: EventoTipoDisparo | "";
  modoAvaliacao?: EventoModoAvaliacao | "";
  page: number;
  size: number;
}

export interface MissionRequestFilters {
  status?: EventoAvaliacaoRequestStatus | "";
  medicaoId?: string;
  page: number;
  size: number;
}

export interface MissionWindowFilters {
  status?: EventoJanelaAvaliacaoStatus | "";
  eventoDefinicaoId?: string;
  compartimentoId?: string;
  periodoAulaId?: string;
  page: number;
  size: number;
}

export interface MissionOccurrenceFilters {
  status?: EventoOcorrenciaStatus | "";
  eventoDefinicaoId?: string;
  compartimentoId?: string;
  periodoAulaId?: string;
  page: number;
  size: number;
}
