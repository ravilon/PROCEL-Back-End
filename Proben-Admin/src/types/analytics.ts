export interface NumericBucket {
  sensorExternalId: string;
  sensorNome?: string | null;
  parametroDefId: string;
  parametroNome: string;
  unidade?: string | null;
  compartimentoId: string;
  bucketStart: string;
  bucketEnd: string;
  averageValue: number;
  minimumValue: number;
  maximumValue: number;
  sampleCount: number;
  aggregationVersion: number;
}

export interface NumericBucketSummary {
  sensorExternalId: string;
  sensorNome?: string | null;
  parametroDefId: string;
  parametroNome: string;
  unidade?: string | null;
  compartimentoId: string;
  from: string;
  to: string;
  averageValue: number;
  minimumValue: number;
  maximumValue: number;
  sampleCount: number;
  aggregationVersion: number;
  bucketCount: number;
}

export interface NumericBucketPage {
  content: NumericBucket[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface NumericBucketFilters {
  from: string;
  to: string;
  sensorExternalId?: string;
  sensorTypeName?: string;
  parametroDefId?: string;
  compartimentoId?: string;
  aggregationVersion?: number;
  page?: number;
  size?: number;
}

export type NumericBucketSummaryFilters = Omit<
  NumericBucketFilters,
  "page" | "size"
>;

export interface AnalyticsSensorOption {
  externalId: string;
  nome: string;
  compartimentoId?: string;
  compartimentoNome?: string;
}

export interface AnalyticsParametroOption {
  id: string;
  nome: string;
  unidade?: string | null;
  tipoNome?: string;
}

export interface AnalyticsCompartimentoOption {
  id: string;
  nome: string;
  tipo?: string;
  predioNome?: string;
  campusNome?: string;
  unidadeNome?: string;
}

export interface AnalyticsPeriod {
  from: string;
  to: string;
}

export interface AggregationJob {
  id: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | string;
  from: string;
  to: string;
  windowDuration: string;
  sensorExternalId?: string | null;
  compartimentoId?: string | null;
  error?: string | null;
  progress: {
    totalWindows: number;
    completedWindows: number;
    failedWindows: number;
    processingWindows: number;
  };
}

export interface CreateAggregationJobRequest {
  from: string;
  to: string;
  windowDuration: string;
  sensorExternalId?: string;
  compartimentoId?: string;
}

// O backend serializa BigDecimal como JSON number. O Admin usa number somente
// para exibicao e graficos, sem persistir calculos derivados.
export type AnalyticsDecimal = number;
