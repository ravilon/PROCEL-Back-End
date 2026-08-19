export type { Role, Session } from "./auth";
export type { ApiErrorBody } from "./errors";
export type { Compartimento, CompartimentoFilterOptions, Disciplina, PeriodoAula } from "./catalog";
export type { AlunoDisciplinaStatus, Curso, DisciplinaAluno, Pessoa, PessoaCurso, PessoaResumo } from "./people";
export type { Atividade, AtividadeStatus, Missao } from "./missions";
export type { AvaliacaoResultado, GrupoRegra, Medicao, ParametroDef, ParametroQualificacao, RegraParametro, Sensor, SensorDataType, SensorGrupoRegra, TipoSensor } from "./sensors";
export type { AulasSyncJob, AulasSyncJobStatus, AulasSyncProgress, AulasSyncResult, RoomsSyncResult } from "./sync";
export type {
  RawTelemetryEvent,
  RawTelemetryEventPage,
  RawTelemetryProcessing,
  RawTelemetryReprocessAuditEntry,
  RawTelemetryReprocessing,
  RawTelemetryStatus,
  ReprocessTelemetryResponse,
  TelemetryEventFilters,
  TelemetrySource,
} from "./telemetry";
