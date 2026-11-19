/** GET /api/resources/{resourceId}/occupancy: el calendario de un espacio para Dirección de Programa. */
export type UseKind = "CLASS" | "EXTRAORDINARY" | "STUDENT_RESERVATION";

export interface ScheduledUse {
  kind: UseKind;
  start: string;
  end: string;
  course?: string;
  program?: string;
}

export interface OccupancyResponse {
  resourceId: string;
  name: string;
  category: "ESPACIO" | "ACTIVO";
  location?: string;
  operationalStatus: "DISPONIBLE" | "EN_USO" | "EN_MANTENIMIENTO";
  from: string;
  to: string;
  uses: ScheduledUse[];
}
