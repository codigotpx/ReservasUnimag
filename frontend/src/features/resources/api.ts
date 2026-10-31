import { apiFetch } from "@/lib/api/client";
import type { Category, SearchResourcesParams, SearchResourcesResponse } from "./types";

/** GET /api/resources (UC1 Contratos §1). */
export function searchResources(params: SearchResourcesParams): Promise<SearchResourcesResponse> {
  return apiFetch<SearchResourcesResponse>(`/resources?${toQuery(params)}`);
}

/** La búsqueda vive en la URL, para que se pueda recargar, compartir y volver atrás. */
export function toQuery(params: SearchResourcesParams): string {
  const query = new URLSearchParams({ date: params.date, start: params.start, end: params.end });
  if (params.category) query.set("category", params.category);
  if (params.type) query.set("type", params.type);
  if (params.minCapacity) query.set("minCapacity", String(params.minCapacity));
  if (params.page && params.page > 1) query.set("page", String(params.page));
  return query.toString();
}

/** La búsqueda escrita en la URL, o null si falta la franja. */
export function fromQuery(query: URLSearchParams): SearchResourcesParams | null {
  const date = query.get("date");
  const start = query.get("start");
  const end = query.get("end");
  if (!date || !start || !end) return null;
  const minCapacity = Number(query.get("minCapacity"));
  const page = Number(query.get("page"));
  return {
    date,
    start,
    end,
    category: categoryOf(query.get("category")),
    type: query.get("type") ?? undefined,
    minCapacity: minCapacity >= 1 ? minCapacity : undefined,
    page: page >= 1 ? page : 1,
  };
}

export function categoryOf(value: string | null): Category {
  return value === "ACTIVO" ? "ACTIVO" : "ESPACIO";
}
