import { apiFetch } from "@/lib/api/client";
import type { PendingReturnsResponse, PendingReviewsResponse } from "./types";

/** GET /api/loans/pending-returns (UC12 Contratos §3): préstamos sin devolver, los más atrasados primero. */
export function fetchPendingReturns(page = 1): Promise<PendingReturnsResponse> {
  return apiFetch<PendingReturnsResponse>(`/loans/pending-returns?page=${page}`);
}

/** GET /api/reservations/pending-reviews (UC12 Contratos §4): espacios terminados sin check-out. */
export function fetchPendingReviews(
  page = 1,
  origin?: "ESTUDIANTIL" | "ACADEMICO",
): Promise<PendingReviewsResponse> {
  const query = new URLSearchParams({ page: String(page) });
  if (origin) query.set("origin", origin);
  return apiFetch<PendingReviewsResponse>(`/reservations/pending-reviews?${query}`);
}
