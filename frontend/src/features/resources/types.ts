/** Traducción literal de UC1 Contratos §1 y §5. Lo que el backend omite es opcional aquí. */
export type Category = "ESPACIO" | "ACTIVO";

export type DisplayedStatus =
  | "DISPONIBLE"
  | "RESERVADO"
  | "EN_USO"
  | "BLOQUEO_ACADEMICO"
  | "EN_MANTENIMIENTO";

export interface TimeSlot {
  date: string;
  start: string;
  end: string;
}

export interface ResourceWithStatus {
  id: string;
  name: string;
  category: Category;
  type: string;
  location: string;
  faculty?: string; // espacio
  maxCapacity?: number; // espacio
  equipment?: string[]; // espacio
  condition?: string; // activo
  loanTermBusinessDays?: number; // activo
  status: DisplayedStatus;
  selectable: boolean;
  reason?: string;
  occupiedUntil?: string; // UC8 FR-012
}

export interface SanctionNotice {
  type: "ACTIVE_SANCTION" | "NOT_VERIFIED";
  reason?: string;
  until?: string;
  message: string;
}

export interface SearchResourcesParams {
  date: string;
  start: string;
  end: string;
  category?: Category;
  type?: string;
  minCapacity?: number;
  page?: number;
}

export interface SearchResourcesResponse {
  timeSlot: TimeSlot;
  filters: { category: Category | null; type: string | null; minCapacity: number | null };
  pagination: { page: number; pageSize: number; totalPages: number; total: number };
  anySelectable: boolean;
  catalogTruncated: boolean;
  queriedAt: string;
  resources: ResourceWithStatus[];
  message?: string;
  nextTimeSlot?: TimeSlot;
  sanctionNotice?: SanctionNotice;
}
