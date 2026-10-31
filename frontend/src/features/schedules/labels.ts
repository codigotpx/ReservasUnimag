import { BLOCKING_VERDICTS, type DayOfWeek, type Verdict } from "./types";

/** Etiqueta y colores de cada dictamen, como en el diseño (admin-importar-horarios). */
export const VERDICTS: Record<Verdict, { label: string; className: string }> = {
  OK: { label: "Se creará el bloqueo", className: "bg-green-600/10 text-green-700" },
  UNCHANGED: { label: "Ya existe, se omite", className: "bg-slate-400/15 text-slate-600" },
  DISPLACES_RESERVATIONS: { label: "Desplaza reservas", className: "bg-amber-500/15 text-amber-700" },
  REJECTED_FORMAT: { label: "Rechazada: error de formato", className: "bg-red-600/10 text-red-700" },
  REJECTED_RESOURCE: { label: "Rechazada: el recurso no existe", className: "bg-red-600/10 text-red-700" },
  ACADEMIC_CLASH: { label: "Rechazada: choca con otra clase", className: "bg-red-600/10 text-red-700" },
  ASSET_ALREADY_PICKED_UP: { label: "Rechazada: activo ya prestado", className: "bg-red-600/10 text-red-700" },
  RESOURCE_UNDER_MAINTENANCE: { label: "Rechazada: en mantenimiento", className: "bg-red-600/10 text-red-700" },
};

export const DAY_LABELS: Record<DayOfWeek, string> = {
  LUNES: "Lunes",
  MARTES: "Martes",
  MIERCOLES: "Miércoles",
  JUEVES: "Jueves",
  VIERNES: "Viernes",
  SABADO: "Sábado",
};

export function isBlocking(verdict: Verdict): boolean {
  return BLOCKING_VERDICTS.includes(verdict);
}

/** Primero lo que hay que corregir, luego lo que desplaza reservas, luego el resto; dentro, por línea del archivo. */
export function verdictOrder(verdict: Verdict): number {
  if (isBlocking(verdict)) return 0;
  if (verdict === "DISPLACES_RESERVATIONS") return 1;
  return verdict === "OK" ? 2 : 3;
}

/** Los periodos que se ofrecen: el actual, el anterior y el siguiente. */
export function termOptions(now = new Date()): { options: string[]; current: string } {
  const year = now.getFullYear();
  const current = `${year}-${now.getMonth() < 6 ? 1 : 2}`;
  return { options: [`${year - 1}-2`, `${year}-1`, `${year}-2`, `${year + 1}-1`], current };
}
