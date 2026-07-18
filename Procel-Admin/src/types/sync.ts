export interface RoomsSyncResult {
  fetched: number;
  inserted: number;
  updated: number;
  skipped: number;
  elapsedMs: number;
}

export type AulasSyncJobStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export interface AulasSyncProgress {
  roomsTotal: number;
  roomsProcessed: number;
  roomsSynced: number;
  roomsFailed: number;
  progressPercent: number;
}

export interface AulasSyncResult {
  weekStart: string;
  weekEnd: string;
  roomsRequested: number;
  roomsSynced: number;
  roomsFailed: number;
  occurrencesFetched: number;
  occurrencesDeleted: number;
  occurrencesInserted: number;
  disciplinesCreated: number;
  disciplinesUpdated: number;
  elapsedMs: number;
  errors: string[];
}

export interface AulasSyncJob {
  jobId: string;
  status: AulasSyncJobStatus;
  weekStart: string;
  roomId?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  progress: AulasSyncProgress;
  result?: AulasSyncResult | null;
  error?: string | null;
}