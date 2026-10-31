import { dayOf, shortDay, timeOf } from "@/lib/dates";
import type { AffectedReservation, ValidatedRow } from "../types";

/**
 * Las reservas estudiantiles que una carga o una necesidad extraordinaria cancela (FR-008), con quién las tenía y, en
 * una carga, qué fila las desplaza.
 */
export function AffectedList({
  rows,
  reservations,
  resourceName,
}: {
  rows?: ValidatedRow[];
  reservations?: AffectedReservation[];
  resourceName?: string;
}) {
  const items: { row?: ValidatedRow; reservation: AffectedReservation }[] = rows
    ? rows.flatMap((row) => (row.affectedReservations ?? []).map((reservation) => ({ row, reservation })))
    : (reservations ?? []).map((reservation) => ({ reservation }));
  return (
    <div className="max-h-80 overflow-auto rounded-2xl border border-gray-200">
      <table className="w-full min-w-[40rem] text-left text-[13px]">
        <thead className="sticky top-0 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
          <tr>
            <th className="px-3 py-3">Estudiante</th>
            <th className="px-3 py-3">Recurso</th>
            <th className="px-3 py-3">Reserva</th>
            {rows && <th className="px-3 py-3">La desplaza</th>}
          </tr>
        </thead>
        <tbody>
          {items.map(({ row, reservation }) => (
            <tr key={reservation.reservationId} className="border-t border-gray-200">
              <td className="px-3 py-3">
                <p className="font-medium text-foreground">{reservation.holder.name}</p>
                <p className="text-xs text-gray-500">{reservation.holder.code}</p>
              </td>
              <td className="px-3 py-3 text-foreground">{row ? (row.resourceName ?? row.resourceId) : resourceName}</td>
              <td className="whitespace-nowrap px-3 py-3 text-foreground">
                {shortDay(dayOf(reservation.start))}, {timeOf(reservation.start)} - {timeOf(reservation.end)}
              </td>
              {row && (
                <td className="px-3 py-3 text-gray-600">
                  Fila {row.row}: {row.course}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

