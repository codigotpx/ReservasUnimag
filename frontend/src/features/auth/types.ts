export type Role = "ESTUDIANTE" | "DIRECCION_PROGRAMA";

/** GET /api/auth/me (UC1 Contratos §4). */
export interface Profile {
  id: string;
  code: string;
  name: string;
  role: Role;
}

export const ROLE_LABELS: Record<Role, string> = {
  ESTUDIANTE: "Estudiante",
  DIRECCION_PROGRAMA: "Dirección de Programa",
};

/** El Estudiante reserva; Dirección de Programa no (UC1 FR-016). */
export function canReserve(role: Role): boolean {
  return role === "ESTUDIANTE";
}

/** La pantalla de inicio de cada rol después del login. */
export function homeFor(role: Role): string {
  return role === "DIRECCION_PROGRAMA" ? "/schedules" : "/resources";
}
