import { dayOf, monthYear, shortDay, timeOf } from "@/lib/dates";
import type { Reservation } from "../types";

/** El historial agrupado por mes, de la más reciente a la más antigua. */
export function HistoryList({ reservations }: { reservations: Reservation[] }) {
  const sorted = [...reservations].sort((a, b) => Date.parse(b.start) - Date.parse(a.start));
  const months = new Map<string, Reservation[]>();
  for (const reservation of sorted) {
    const month = monthYear(dayOf(reservation.start));
    months.set(month, [...(months.get(month) ?? []), reservation]);
  }

  return (
    <div className="flex flex-col gap-6">
      {[...months].map(([month, items]) => (
        <section key={month}>
          <h2 className="mb-3 font-semibold text-gray-500">{month}</h2>
          <ul className="divide-y divide-gray-200 rounded-3xl border border-gray-200 bg-white px-5">
            {items.map((reservation) => {
              const badge = badgeOf(reservation);
              return (
                <li key={reservation.id} className="flex items-center justify-between gap-3 py-4">
                  <div className="min-w-0">
                    <p className="truncate text-lg font-semibold text-foreground">
                      {reservation.name ?? reservation.resourceId}
                    </p>
                    <p className="text-gray-500">{period(reservation)}</p>
                    {badge.note && <p className="mt-1 text-sm text-violet-800">{badge.note}</p>}
                    {reservation.absence && <p className="mt-1 text-sm text-red-700">{reservation.absence.message}</p>}
                  </div>
                  <span className={`shrink-0 rounded-lg px-3 py-1 text-sm font-semibold uppercase ${badge.className}`}>
                    {badge.label}
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

function badgeOf(reservation: Reservation): { label: string; className: string; note?: string } {
  const gray = "bg-gray-100 text-gray-600";
  if (reservation.absence) return { label: "Ausencia registrada", className: "bg-red-100 text-red-700" };
  switch (reservation.status) {
    case "CANCELADA":
      return { label: "Cancelada", className: gray };
    case "CANCELADA_POR_PRIORIDAD_ACADEMICA":
      return {
        label: "Prioridad académica",
        className: "bg-violet-100 text-violet-800",
        note: "La canceló una clase: no cuenta en tu contra.",
      };
    case "CANCELADA_POR_RECURSO_NO_DISPONIBLE":
      return { label: "No disponible", className: gray };
    case "FINALIZADA":
      return { label: "Finalizada", className: gray };
    case "CONFIRMADA":
      return { label: "Terminada", className: gray };
  }
}

/** "15 sept, 14:00 - 16:00", o de varios días "Del 1 nov al 6 nov". */
function period(reservation: Reservation): string {
  const startDay = dayOf(reservation.start);
  const endDay = dayOf(reservation.end);
  return startDay === endDay
    ? `${shortDay(startDay)}, ${timeOf(reservation.start)} - ${timeOf(reservation.end)}`
    : `Del ${shortDay(startDay)} al ${shortDay(endDay)}`;
}
