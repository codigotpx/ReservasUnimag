import { dayOf, shortDay, timeOf } from "@/lib/dates";
import type { AffectedReservation, ValidatedRow } from "../types";

/** Las reservas estudiantiles que una carga cancela (FR-008), con quién las tenía y qué fila las desplaza. */
export function AffectedList({ rows }: { rows: ValidatedRow[] }) {
  const items: { row: ValidatedRow; reservation: AffectedReservation }[] = rows.flatMap((row) =>
    (row.affectedReservations ?? []).map((reservation) => ({ row, reservation })),
  );
  return (
    <div className="max-h-80 overflow-auto rounded-lg border border-[#dde3e9]">
      <table className="w-full min-w-[40rem] text-left text-[13px]">
        <thead className="sticky top-0 bg-[#f4f6f8] text-xs font-bold text-gray-500">
          <tr>
            <th className="px-3 py-3">Estudiante</th>
            <th className="px-3 py-3">Recurso</th>
            <th className="px-3 py-3">Reserva</th>
            <th className="px-3 py-3">La desplaza</th>
          </tr>
        </thead>
        <tbody>
          {items.map(({ row, reservation }) => (
            <tr key={reservation.reservationId} className="border-t border-[#dde3e9]">
              <td className="px-3 py-3">
                <p className="font-medium text-foreground">{reservation.holder.name}</p>
                <p className="text-xs text-gray-500">{reservation.holder.code}</p>
              </td>
              <td className="px-3 py-3 text-foreground">{row.resourceName ?? row.resourceId}</td>
              <td className="whitespace-nowrap px-3 py-3 text-foreground">
                {shortDay(dayOf(reservation.start))}, {timeOf(reservation.start)} - {timeOf(reservation.end)}
              </td>
              <td className="px-3 py-3 text-gray-600">
                Fila {row.row}: {row.course}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

