import { apiFetch } from "@/lib/api/client";

/** Lo que usa la consulta de recursos de GET /api/reservations/mine (UC2 Contratos §3): el cupo. */
export interface Quota {
  used: number;
  max: number;
}

export function fetchQuota(): Promise<Quota> {
  return apiFetch<{ quota: Quota }>("/reservations/mine").then((mine) => mine.quota);
}
