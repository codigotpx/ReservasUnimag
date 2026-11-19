import type { Category, DisplayedStatus } from "./types";

/**
 * Los tipos del filtro, por categoría. Es la misma lista de reservations.resource-types del backend:
 * el plan de UC1 no agrega un endpoint de tipos mientras el Módulo 1 no publique el suyo (P-16).
 */
export const TYPES_BY_CATEGORY: Record<Category, string[]> = {
  ESPACIO: ["LABORATORIO", "SALON", "SALA_DE_ESTUDIO", "AUDITORIO"],
  ACTIVO: ["PORTATIL", "VIDEOBEAM", "MICROSCOPIO", "LIBRO"],
};

export const CATEGORY_LABELS: Record<Category, string> = {
  ESPACIO: "Espacios",
  ACTIVO: "Activos",
};

const CODE_LABELS: Record<string, string> = {
  SALON: "Salón",
  LABORATORIO: "Laboratorio",
  SALA_DE_ESTUDIO: "Sala de estudio",
  AUDITORIO: "Auditorio",
  LIBRO: "Libro",
  MICROSCOPIO: "Microscopio",
  VIDEOBEAM: "Videobeam",
  PORTATIL: "Portátil",
  PROYECTOR: "Proyector",
  AIRE_ACONDICIONADO: "Aire acondicionado",
  SONIDO: "Sonido",
};

/** Los tipos y el equipamiento son códigos opacos del Módulo 1: los conocidos se traducen y el resto se humaniza. */
export function codeLabel(code: string): string {
  const known = CODE_LABELS[code];
  if (known) return known;
  const words = code.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function statusLabel(status: DisplayedStatus, category: Category): string {
  switch (status) {
    case "DISPONIBLE":
      return "Disponible";
    case "RESERVADO":
      return "Reservado";
    case "EN_USO":
      return category === "ACTIVO" ? "En préstamo" : "En uso";
    case "BLOQUEO_ACADEMICO":
      return "Clase";
    case "EN_MANTENIMIENTO":
      return "Mantenimiento";
  }
}
