import { searchResources } from "@/features/resources/api";
import type { ResourceWithStatus } from "@/features/resources/types";
import { apiFetch } from "@/lib/api/client";
import type { OccupancyResponse } from "./types";

export function fetchOccupancy(resourceId: string, from: string, to: string): Promise<OccupancyResponse> {
  const query = new URLSearchParams({ from, to });
  return apiFetch<OccupancyResponse>(`/resources/${encodeURIComponent(resourceId)}/occupancy?${query}`);
}

/**
 * Los espacios del catálogo, para elegir cuál mirar. Sale de la consulta de UC1 sobre el día completo, que ya trae
 * nombre y ubicación; se piden como mucho 10 páginas.
 */
export async function fetchSpaces(date: string): Promise<ResourceWithStatus[]> {
  const spaces: ResourceWithStatus[] = [];
  for (let page = 1; page <= 10; page++) {
    const result = await searchResources({ date, start: "06:00", end: "22:00", category: "ESPACIO", page });
    spaces.push(...result.resources);
    if (page >= result.pagination.totalPages) break;
  }
  return spaces;
}
