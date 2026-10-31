import { apiFetch } from "@/lib/api/client";
import type {
  CancellationResponse,
  CreateReservationRequest,
  ExpectedDueDate,
  MyReservationsResponse,
  Quota,
  Reservation,
} from "./types";

/** GET /api/reservations/mine (UC2 Contratos §3): las vigentes, o con onlyActive en false también las cerradas. */
export function fetchMyReservations(onlyActive = true): Promise<MyReservationsResponse> {
  return apiFetch<MyReservationsResponse>(`/reservations/mine?vigentes=${onlyActive}`);
}

/** El cupo sale de la misma lista. */
export function fetchQuota(): Promise<Quota> {
  return fetchMyReservations().then((mine) => mine.quota);
}

/** DELETE /api/reservations/{id} (UC4 Contratos §1). Es idempotente: la segunda vez trae alreadyCancelled. */
export function cancelReservation(reservationId: string): Promise<CancellationResponse> {
  return apiFetch<CancellationResponse>(`/reservations/${encodeURIComponent(reservationId)}`, { method: "DELETE" });
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
