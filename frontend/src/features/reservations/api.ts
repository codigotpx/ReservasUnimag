import { apiFetch } from "@/lib/api/client";
import type {
  CreateReservationRequest,
  ExpectedDueDate,
  MyReservationsResponse,
  Quota,
  Reservation,
} from "./types";

/** El cupo sale de GET /api/reservations/mine (UC2 Contratos §3). */
export function fetchQuota(): Promise<Quota> {
  return apiFetch<MyReservationsResponse>("/reservations/mine").then((mine) => mine.quota);
}

/** POST /api/reservations (UC2 Contratos §1). Las denegaciones llegan como ApiRequestError con un ReservationError. */
export function createReservation(request: CreateReservationRequest): Promise<Reservation> {
  return apiFetch<Reservation>("/reservations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

/** GET /api/loans/expected-due-date (UC2 Contratos §2): el vencimiento antes de confirmar un préstamo. */
export function fetchExpectedDueDate(resourceId: string, date: string, pickup: string): Promise<ExpectedDueDate> {
  const query = new URLSearchParams({ resourceId, date, pickup });
  return apiFetch<ExpectedDueDate>(`/loans/expected-due-date?${query}`);
}
