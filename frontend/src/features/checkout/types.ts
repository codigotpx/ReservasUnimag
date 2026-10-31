/** UC12 Contratos §3 y §4: lo que espera un check-out. Lo que el backend omite es opcional aquí. */
export interface Holder {
  code: string;
  name?: string;
}

export interface Pagination {
  page: number;
  pageSize: number;
  totalPages: number;
  total: number;
}

export interface PendingReturn {
  reservationId: string;
  resourceId: string;
  resourceName?: string;
  holder?: Holder;
  pickup: string;
  dueAt: string;
  overdueDays: number;
  lossThresholdAt?: string;
}

export interface PendingReturnsResponse {
  loans: PendingReturn[];
  pagination: Pagination;
  summary: { total: number; overdue: number; nearLossThreshold: number };
}

export interface PendingReview {
  reservationId: string;
  resourceId: string;
  resourceName?: string;
  origin: "ESTUDIANTIL" | "ACADEMICO";
  holder?: Holder;
  start: string;
  end: string;
  finishedDaysAgo: number;
}

export interface PendingReviewsResponse {
  reservations: PendingReview[];
  pagination: Pagination;
  summary: { total: number; student: number; academic: number };
}
