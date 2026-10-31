export type Role = "ESTUDIANTE" | "MONITOR" | "DIRECCION_PROGRAMA";

/** GET /api/auth/me (UC1 Contratos §4). */
export interface Profile {
  id: string;
  code: string;
  name: string;
  role: Role;
}

export const ROLE_LABELS: Record<Role, string> = {
  ESTUDIANTE: "Estudiante",
  MONITOR: "Monitor",
  DIRECCION_PROGRAMA: "Dirección de Programa",
};

/** Estudiante y Monitor reservan; Dirección de Programa no (UC1 FR-016). */
export function canReserve(role: Role): boolean {
  return role === "ESTUDIANTE" || role === "MONITOR";
}
