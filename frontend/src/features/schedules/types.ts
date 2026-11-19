/** Traducción literal de UC3 Contratos §8. Lo que el backend omite es opcional aquí. */
import type { ApiError } from "@/lib/api/client";
import type { Category } from "@/features/resources/types";

export type DayOfWeek = "LUNES" | "MARTES" | "MIERCOLES" | "JUEVES" | "VIERNES" | "SABADO";

export type Verdict =
  | "OK"
  | "UNCHANGED"
  | "DISPLACES_RESERVATIONS"
  | "REJECTED_FORMAT"
  | "REJECTED_RESOURCE"
  | "ACADEMIC_CLASH"
  | "ASSET_ALREADY_PICKED_UP"
  | "RESOURCE_UNDER_MAINTENANCE";

export type UploadStatus = "VALIDATED" | "APPLIED" | "REJECTED" | "EXPIRED";

/** Los dictámenes que impiden aplicar la carga. */
export const BLOCKING_VERDICTS: Verdict[] = [
  "REJECTED_FORMAT",
  "REJECTED_RESOURCE",
  "ACADEMIC_CLASH",
  "ASSET_ALREADY_PICKED_UP",
  "RESOURCE_UNDER_MAINTENANCE",
];

export interface AffectedReservation {
  reservationId: string;
  holder: { code: string; name: string };
  start: string;
  end: string;
  category: Category;
}

export interface ValidatedRow {
  row: number;
  resourceId: string;
  resourceName?: string;
  course: string;
  courseCode: string;
  group: string;
  dayOfWeek: DayOfWeek;
  start: string;
  end: string;
  verdict: Verdict;
  sessions: number;
  problem?: string;
  column?: string;
  conflictingBlock?: { course: string; courseCode: string; group: string };
  affectedReservations?: AffectedReservation[];
}

export interface ValidationSummary {
  rowsRead: number;
  rowsOk: number;
  rowsUnchanged: number;
  rowsWithBlockingVerdict: number;
  sessionsToCreate: number;
  sessionsSkippedNonBusinessDay: number;
  blocksToWithdraw: number;
  reservationsToCancel: number;
}

export interface ValidationResponse {
  uploadId: string;
  academicTerm: string;
  term: { start: string; end: string };
  status: UploadStatus;
  validatedAt: string;
  expiresAt?: string;
  applicable: boolean;
  requiresConfirmation: boolean;
  summary: ValidationSummary;
  rows: ValidatedRow[];
}

export interface ConfirmationResponse {
  uploadId: string;
  academicTerm: string;
  status: "APPLIED";
  appliedAt: string;
  appliedBy: { userId: string; name: string };
  result: {
    blocksCreated: number;
    blocksUnchanged: number;
    blocksWithdrawn: number;
    reservationsCancelled: number;
    eventsQueued: number;
  };
}

export interface ScheduleError extends ApiError {
  code?:
    | "EMPTY_FILE"
    | "INVALID_HEADER"
    | "INVALID_TERM"
    | "UPLOAD_NOT_APPLICABLE"
    | "UPLOAD_EXPIRED"
    | "UPLOAD_ALREADY_APPLIED"
    | "IMPACT_CHANGED";
  verdict?: Verdict;
  missingColumns?: string[];
  reservationsToCancelAtValidation?: number;
  reservationsToCancelNow?: number;
  expiredAt?: string;
  appliedAt?: string;
}

/** Una carga del histórico (UC3 Contratos §4). */
export interface ScheduleUploadItem {
  uploadId: string;
  academicTerm: string;
  status: UploadStatus;
  fileName?: string;
  uploadedBy: { userId: string; name?: string };
  validatedAt: string;
  appliedAt?: string;
  result?: { blocksCreated: number; blocksUnchanged: number; blocksWithdrawn: number; reservationsCancelled: number };
}

export interface ScheduleUploadsResponse {
  uploads: ScheduleUploadItem[];
  pagination: { page: number; pageSize: number; totalPages: number; total: number };
}

/** GET /api/schedules/{uploadId}: el objeto del listado más el periodo, el resumen y las filas. */
export interface ScheduleUploadDetail extends ScheduleUploadItem {
  term: { start: string; end: string };
  expiresAt?: string;
  applicable: boolean;
  summary: ValidationSummary;
  rows: ValidatedRow[];
}

/** UC3 Contratos §3: una necesidad extraordinaria, una sola franja. */
export interface ExtraordinaryNeedRequest {
  resourceId: string;
  date: string;
  start: string;
  end: string;
  course: string;
  courseCode: string;
  group: string;
  program: string;
  instructor?: string;
  confirm?: boolean;
}

export interface ExtraordinaryNeedResponse {
  applied: boolean;
  verdict: Verdict;
  resourceId?: string;
  resourceName?: string;
  start?: string;
  end?: string;
  affectedReservations?: AffectedReservation[];
  block?: {
    reservationId: string;
    resourceId: string;
    type: "REGULAR" | "EXTRAORDINARIO";
    start: string;
    end: string;
    course: string;
    program: string;
    instructor?: string;
  };
  reservationsCancelled?: number;
  eventsQueued?: number;
}
