"use client";

import { useState } from "react";
import { DAY_LABELS, VERDICTS, isBlocking, verdictOrder } from "../labels";
import type { ValidatedRow } from "../types";

type Filter = "all" | "problems" | "displaces";

/**
 * Las filas del archivo con su resultado (UC3 T033). Primero las que hay que corregir; un semestre son cientos de
 * filas, así que la tabla tiene su propio scroll y filtros.
 */
export function ReportTable({ rows }: { rows: ValidatedRow[] }) {
  const problems = rows.filter((row) => isBlocking(row.verdict)).length;
  const displaces = rows.filter((row) => row.verdict === "DISPLACES_RESERVATIONS").length;
  const [filter, setFilter] = useState<Filter>(problems > 0 ? "problems" : "all");

  const visible = rows
    .filter((row) =>
      filter === "problems"
        ? isBlocking(row.verdict)
        : filter === "displaces"
          ? row.verdict === "DISPLACES_RESERVATIONS"
          : true,
    )
    .sort((a, b) => verdictOrder(a.verdict) - verdictOrder(b.verdict) || a.row - b.row);

  return (
    <div className="flex flex-col gap-3">
      <div role="tablist" aria-label="Filtrar filas" className="flex flex-wrap gap-2">
        {(
          [
            ["all", `Todas (${rows.length})`],
            ["problems", `Con problemas (${problems})`],
            ["displaces", `Desplazan reservas (${displaces})`],
          ] as [Filter, string][]
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={filter === value}
            onClick={() => setFilter(value)}
            className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition ${
              filter === value
                ? "border-brand-navy bg-brand-navy text-white"
                : "border-gray-200 bg-white text-gray-600 hover:border-gray-300"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="max-h-[32rem] overflow-auto rounded-2xl border border-gray-200">
        <table className="w-full min-w-[56rem] text-left text-sm">
          <thead className="sticky top-0 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
            <tr>
              <th className="w-16 px-3 py-3">Fila</th>
              <th className="px-3 py-3">Asignatura</th>
              <th className="px-3 py-3">Recurso</th>
              <th className="px-3 py-3">Día</th>
              <th className="px-3 py-3">Franja</th>
              <th className="px-3 py-3 text-right">Resultado</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((row) => (
              <tr key={row.row} className="border-t border-gray-100 align-top">
                <td className="px-3 py-3 font-medium text-gray-500">{row.row}</td>
                <td className="px-3 py-3">
                  <p className="font-medium text-foreground">{row.course}</p>
                  <p className="text-xs text-gray-500">
                    {row.courseCode} · grupo {row.group}
                    {row.sessions > 0 && ` · ${row.sessions} sesiones`}
                  </p>
                  {row.problem && <p className="mt-1 text-xs text-red-700">{row.problem}</p>}
                </td>
                <td className="px-3 py-3 font-medium text-foreground">{row.resourceName ?? row.resourceId}</td>
                <td className="px-3 py-3 font-medium text-foreground">{DAY_LABELS[row.dayOfWeek] ?? row.dayOfWeek}</td>
                <td className="whitespace-nowrap px-3 py-3 font-medium text-foreground">
                  {row.start} - {row.end}
                </td>
                <td className="px-3 py-3 text-right">
                  <span
                    className={`inline-block whitespace-nowrap rounded-lg px-2.5 py-1 text-xs font-semibold ${VERDICTS[row.verdict].className}`}
                  >
                    {row.verdict === "DISPLACES_RESERVATIONS" && row.affectedReservations
                      ? `Desplaza ${row.affectedReservations.length} ${row.affectedReservations.length === 1 ? "reserva" : "reservas"}`
                      : VERDICTS[row.verdict].label}
                  </span>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-8 text-center text-gray-500">
                  No hay filas con este filtro.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-gray-500">
        La fila es la línea del archivo (la 1 es la cabecera). Una sola fila rechazada impide aplicar la carga.
      </p>
    </div>
  );
}
