/** Traducción literal de UC2 Contratos §7. Lo que el backend omite es opcional aquí. */
import type { ApiError } from "@/lib/api/client";
import type { Category } from "@/features/resources/types";

export type ReservationStatus =
  | "CONFIRMADA"
  | "CANCELADA"
  | "CANCELADA_POR_PRIORIDAD_ACADEMICA"
  | "CANCELADA_POR_RECURSO_NO_DISPONIBLE"
  | "FINALIZADA";

export type DenialCode = "RES-001" | "RES-002" | "RES-003" | "RES-004";

export interface Quota {
  used: number;
  max: number;
}

export interface Loan {
  pickup: string;
  dueAt: string;
  termBusinessDays: number;
  pickedUp: boolean;
  overdue?: boolean; // solo en GET /api/reservations/mine
}

/** Cuerpo de POST /api/reservations: una forma u otra, nunca las dos. */
export type CreateReservationRequest =
  | { resourceId: string; date: string; start: string; end: string }
  | { resourceId: string; date: string; pickup: string };

export interface Reservation {
  id: string;
  resourceId: string;
  name?: string; // se omite si el Módulo 1 no respondió
  category: Category;
  status: ReservationStatus;
  origin?: "ESTUDIANTIL" | "ACADEMICO";
  start: string;
  end: string;
  loan?: Loan; // solo activos
  createdAt?: string;
  quota?: Quota; // viene en la respuesta de creación
}

export interface ExpectedDueDate {
  resourceId: string;
  name: string;
  pickup: string;
  dueAt: string;
  termBusinessDays: number;
  skippedNonBusinessDays: string[];
}

export interface MyReservationsResponse {
  reservations: Reservation[];
  quota: Quota;
  catalogUnavailable: boolean;
}

/** Extensiones que puede traer un ProblemDetail de este caso de uso. */
export interface ReservationError extends ApiError {
  code?: DenialCode | string;
  detectedStatus?: "EN_MANTENIMIENTO";
  active?: number;
  max?: number;
  nextRelease?: string;
  sanctionReason?: string;
  sanctionUntil?: string;
  occupiedUntil?: string;
  limitMinutes?: number;
  requestedMinutes?: number;
  resourceId?: string;
  dueAt?: string;
}
