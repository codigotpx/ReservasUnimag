import { TriangleAlert } from "lucide-react";
import { dayMonth, dayOf, timeOf } from "@/lib/dates";
import type { ReservationError } from "../types";

/**
 * Un texto por código, armado con los campos del error (UC2 T036). El detail del backend queda
 * de respaldo para lo que la pantalla todavía no conozca.
 */
export function DenialMessage({ error }: { error: ReservationError }) {
  const { title, hint } = describe(error);
  return (
    <div role="alert" className="flex gap-3 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-800">
      <TriangleAlert className="mt-0.5 size-5 shrink-0" />
      <div>
        <p className="font-semibold">{title}</p>
        {hint && <p className="mt-1">{hint}</p>}
      </div>
    </div>
  );
}

function describe(error: ReservationError): { title: string; hint?: string } {
  switch (error.code) {
    case "RES-001":
      return { title: "Ese horario está reservado para una clase.", hint: "Prueba con otra franja o con otro recurso." };
    case "RES-002":
      return {
        title: `Ya tienes ${error.active ?? 3} de ${error.max ?? 3} reservas vigentes.`,
        hint: error.nextRelease
          ? `La próxima se libera el ${dayMonth(dayOf(error.nextRelease))} a las ${timeOf(error.nextRelease)}.`
          : error.detail,
      };
    case "RES-003":
      return {
        title: error.sanctionUntil
          ? `Tienes una sanción vigente hasta el ${dayMonth(error.sanctionUntil)}.`
          : "Tienes una sanción vigente.",
        hint: error.sanctionReason ? `Motivo: ${error.sanctionReason}` : "Mientras dure no puedes reservar.",
      };
    case "RES-004":
      return error.detectedStatus === "EN_MANTENIMIENTO"
        ? { title: "El recurso está en mantenimiento.", hint: "No se puede reservar hasta que vuelva a estar disponible." }
        : { title: "Alguien acaba de tomar este recurso.", hint: "Vuelve a consultar la disponibilidad." };
    default:
      return { title: error.detail };
  }
}
