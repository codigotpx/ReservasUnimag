import { CircleAlert, Clock } from "lucide-react";
import { dayInSentence, dayOf, fullDayLabel, relativeDay, dayMonth, timeOf, timeUntil } from "@/lib/dates";
import type { Reservation } from "../types";

/**
 * Una reserva vigente (UC4 T020). El botón de cancelar solo aparece cuando el backend dice que se puede
 * (cancellable); si no, se explica por qué.
 */
export function ActiveReservationCard({
  reservation,
  now,
  onCancel,
}: {
  reservation: Reservation;
  now: number;
  onCancel: () => void;
}) {
  const badge = badgeOf(reservation, now);
  const timing = timingOf(reservation, now);

  return (
    <article className="rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <h3 className="min-w-0 truncate text-lg font-semibold text-foreground">
          {reservation.name ?? reservation.resourceId}
        </h3>
        <span className={`shrink-0 rounded-lg px-3 py-1 text-sm font-semibold uppercase ${badge.className}`}>
          {badge.label}
        </span>
      </div>

      {reservation.loan ? (
        <>
          <p className="mt-2 text-gray-500">Placa: {reservation.resourceId}</p>
          {reservation.loan.pickedUp && <LoanProgress reservation={reservation} now={now} />}
        </>
      ) : (
        <p className="mt-2 text-gray-500">
          {fullDayLabel(dayOf(reservation.start))} · {timeOf(reservation.start)} - {timeOf(reservation.end)}
        </p>
      )}

      <p className={`mt-2 flex items-start gap-2 font-semibold ${timing.className}`}>
        <timing.icon className="mt-0.5 size-5 shrink-0" />
        <span>{timing.text}</span>
      </p>

      <div className="mt-4 border-t border-gray-200 pt-4">
        {reservation.cancellable ? (
          <>
            <button
              type="button"
              onClick={onCancel}
              className="h-12 w-full rounded-full border-2 border-red-700 font-semibold text-red-700 transition hover:bg-red-50"
            >
              Cancelar
            </button>
            <p className="mt-3 text-sm text-gray-500">Puedes cancelar hasta 10 minutos antes.</p>
          </>
        ) : (
          <p className="text-sm text-gray-500">{whyNotCancellable(reservation)}</p>
        )}
      </div>
    </article>
  );
}

/** La barra del préstamo, de la recogida al vencimiento, con el punto naranja al final. */
function LoanProgress({ reservation, now }: { reservation: Reservation; now: number }) {
  const start = Date.parse(reservation.loan!.pickup);
  const end = Date.parse(reservation.loan!.dueAt);
  const progress = Math.min(100, Math.max(0, ((now - start) / Math.max(1, end - start)) * 100));
  return (
    <div aria-hidden className="mt-4 flex items-center gap-2">
      <span className="size-2.5 shrink-0 rounded-full bg-brand-navy" />
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-gray-200">
        <div
          className={`h-full rounded-full ${reservation.loan!.overdue ? "bg-red-600" : "bg-brand-navy"}`}
          style={{ width: `${progress}%` }}
        />
      </div>
      <span className={`size-2.5 shrink-0 rounded-full ${reservation.loan!.overdue ? "bg-red-600" : "bg-amber-500"}`} />
    </div>
  );
}

function badgeOf(reservation: Reservation, now: number): { label: string; className: string } {
  if (reservation.loan) {
    if (reservation.loan.overdue) return { label: "Vencido", className: "bg-red-100 text-red-700" };
    if (reservation.loan.pickedUp) return { label: "En préstamo", className: "bg-slate-200 text-slate-700" };
    return { label: "Por recoger", className: "bg-green-100 text-green-800" };
  }
  if (reservation.useStartedAt || Date.parse(reservation.start) <= now) {
    return { label: "En uso", className: "bg-amber-100 text-amber-800" };
  }
  return { label: "Confirmada", className: "bg-green-100 text-green-800" };
}

type Timing = { text: string; className: string; icon: typeof Clock };

function timingOf(reservation: Reservation, now: number): Timing {
  const soon = "text-amber-600";
  if (reservation.loan) {
    const { pickup, dueAt, pickedUp, overdue } = reservation.loan;
    if (overdue) {
      return {
        text: `Venció el ${dayMonth(dayOf(dueAt))} a las ${timeOf(dueAt)}. Devuélvelo cuanto antes.`,
        className: "text-red-700",
        icon: CircleAlert,
      };
    }
    if (!pickedUp) {
      return {
        text: `Recógelo ${dayInSentence(dayOf(pickup))} a las ${timeOf(pickup)}`,
        className: "text-brand-navy",
        icon: Clock,
      };
    }
    const relative = relativeDay(dayOf(dueAt));
    return {
      text: relative
        ? `Vence ${relative.toLowerCase()} (${timeOf(dueAt)})`
        : `Vence el ${dayMonth(dayOf(dueAt))} (${timeOf(dueAt)})`,
      className: relative ? soon : "text-brand-navy",
      icon: CircleAlert,
    };
  }

  const start = Date.parse(reservation.start);
  if (start <= now) {
    return { text: `En curso, termina a las ${timeOf(reservation.end)}`, className: "text-brand-navy", icon: Clock };
  }
  // El mismo día, cuenta regresiva; si no, el día y la hora.
  const startDay = dayOf(reservation.start);
  if (relativeDay(startDay) !== "Hoy") {
    return {
      text: `Empieza ${dayInSentence(startDay)} a las ${timeOf(reservation.start)}`,
      className: "text-brand-navy",
      icon: Clock,
    };
  }
  return { text: `Empieza en ${timeUntil(Math.floor((start - now) / 60_000))}`, className: soon, icon: Clock };
}

function whyNotCancellable(reservation: Reservation): string {
  if (reservation.loan?.pickedUp) {
    return "Ya tienes el activo: no se cancela, se devuelve.";
  }
  if (reservation.useStartedAt) {
    return "Ya registraste tu llegada: la reserva está en uso.";
  }
  return "Faltan menos de 10 minutos: ya no se puede cancelar.";
}
