import type { Category, DisplayedStatus } from "./types";

/**
 * Los tipos del filtro, por categoría. Son los tipos de activo del catálogo del Módulo 1 (GET /api/v1/catalogos),
 * escritos tal cual porque el backend filtra comparando el nombre. Los espacios del Módulo 1 no tienen subtipo, así
 * que no hay filtro por tipo de espacio. Es la misma lista de reservations.resource-types del backend.
 */
export const TYPES_BY_CATEGORY: Record<Category, string[]> = {
  ESPACIO: [],
  ACTIVO: [
    "Cómputo Universitario",
    "Cómputo Alto Rendimiento",
    "Multimedia",
    "Audiovisual Producción",
    "Equipo de Laboratorio",
    "Laboratorio Clínico",
    "Equipo Técnico",
    "Mobiliario Universitario",
    "GENERAL",
  ],
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

/**
 * Los tipos y el equipamiento vienen del Módulo 1: los códigos conocidos se traducen, los nombres ya escritos para
 * personas (con minúsculas) se dejan igual y el resto se humaniza. Un espacio no tiene tipo: sin código, texto vacío.
 */
export function codeLabel(code: string | null | undefined): string {
  if (!code) return "";
  const known = CODE_LABELS[code];
  if (known) return known;
  if (code !== code.toUpperCase()) return code;
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
