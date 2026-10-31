import { TriangleAlert } from "lucide-react";
import { dayInSentence, dayMonth, dayOf, timeOf } from "@/lib/dates";
import type { ReservationError } from "../types";

/** Lo que la persona puede hacer después de una denegación; quien abre la hoja decide qué hace cada una. */
export type DenialAction = "change-slot" | "my-reservations" | "close";

export interface Denial {
  title: string;
  badge: string;
  headline: string;
  body: string;
  action: { kind: DenialAction; label: string };
  /** Para RES-002: el cupo lleno que se dibuja como barra. */
  quota?: { used: number; max: number };
}

/** Los cuatro códigos del diccionario (UC2 Contratos §1); cualquier otro error no es una denegación. */
export function isDenial(error: ReservationError): boolean {
  return ["RES-001", "RES-002", "RES-003", "RES-004"].includes(error.code ?? "");
}

/**
 * El texto de cada denegación, según el diseño (docs/diseno/pantallas/error-*.png), armado con los campos del
 * error. Nunca dice quién tiene el recurso (UC8 FR-005).
 */
export function describeDenial(error: ReservationError, resourceName: string): Denial {
  switch (error.code) {
    case "RES-001":
      return {
        title: "Conflicto de reserva",
        badge: "Denegado",
        headline: "Bloqueo docente",
        body: error.occupiedUntil
          ? `${resourceName} está reservado para actividad docente hasta las ${timeOf(error.occupiedUntil)}.`
          : `${resourceName} está reservado para actividad docente en esa franja.`,
        action: { kind: "change-slot", label: "Ver otras franjas" },
      };
    case "RES-002": {
      const used = error.active ?? 3;
      const max = error.max ?? 3;
      return {
        title: "Límite alcanzado",
        badge: "Denegado",
        headline: "Límite de reservas alcanzado",
        body: error.nextRelease
          ? `Tienes ${used} de ${max} reservas vigentes. La próxima se libera ${dayInSentence(dayOf(error.nextRelease))} a las ${timeOf(error.nextRelease)}.`
          : error.detail,
        action: { kind: "my-reservations", label: "Ver mis reservas" },
        quota: { used, max },
      };
    }
    case "RES-003":
      return {
        title: "Usuario restringido",
        badge: "Sancionado",
        headline: "Sanción activa",
        body: [
          error.sanctionUntil ? `Tienes una sanción vigente hasta el ${dayMonth(error.sanctionUntil)}.` : "Tienes una sanción vigente.",
          error.sanctionReason ? `Motivo: ${lowerFirst(error.sanctionReason)}.` : "Mientras dure no puedes reservar.",
        ].join(" "),
        action: { kind: "close", label: "Entendido" },
      };
    default:
      return error.detectedStatus === "EN_MANTENIMIENTO"
        ? {
            title: "Recurso no disponible",
            badge: "Denegado",
            headline: "En mantenimiento",
            body: "El recurso entró en mantenimiento y no se puede reservar hasta que vuelva a estar disponible.",
            action: { kind: "close", label: "Buscar alternativas" },
          }
        : {
            title: "Recurso no disponible",
            badge: "Denegado",
            headline: "El recurso acaba de ser tomado",
            body: "Otra persona confirmó este recurso mientras decidías.",
            action: { kind: "close", label: "Buscar alternativas" },
          };
  }
}

/** El cuerpo de la hoja de denegación; el botón va en el pie de la hoja. */
export function DenialContent({ denial }: { denial: Denial }) {
  return (
    <div role="alert">
      <div className="flex items-start justify-between gap-3 sm:pr-8">
        <h2 className="text-2xl font-bold leading-tight text-foreground">{denial.title}</h2>
        <span className="shrink-0 rounded-lg bg-red-100 px-3 py-1 text-sm font-semibold uppercase text-red-700">
          {denial.badge}
        </span>
      </div>
      <p className="mt-4 flex items-center gap-2 text-lg font-semibold text-red-700">
        <TriangleAlert className="size-6 shrink-0" />
        {denial.headline}
      </p>
      <p className="mt-3 text-lg text-foreground">{denial.body}</p>
      {denial.quota && (
        <div aria-hidden className="mt-5 flex gap-1.5">
          {Array.from({ length: denial.quota.max }, (_, index) => (
            <span
              key={index}
              className={`h-2 flex-1 rounded-full ${index < denial.quota!.used ? "bg-brand-navy" : "bg-gray-200"}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/** Para lo que no es una denegación (un 400 o un 503): el detail del backend en un recuadro. */
export function ProblemMessage({ detail }: { detail: string }) {
  return (
    <p role="alert" className="flex gap-3 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-800">
      <TriangleAlert className="mt-0.5 size-5 shrink-0" />
      {detail}
    </p>
  );
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1).replace(/\.$/, "");
}
