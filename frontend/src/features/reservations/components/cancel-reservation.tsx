"use client";

import { Check, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { BottomSheet } from "@/components/bottom-sheet";
import { ApiRequestError } from "@/lib/api/client";
import { dayOf, shortDay, shortDayLabel, timeOf } from "@/lib/dates";
import { cancelReservation } from "../api";
import type { CancellationError, CancellationResponse, Quota, Reservation } from "../types";

/**
 * Confirmar la cancelación (UC4 T019). Dice qué se libera —en un activo, el periodo entero— y cuánto cupo se recupera.
 * El plazo lo decide el backend: un 409 se muestra tal cual.
 */
export function CancelReservation({
  reservation,
  quota,
  onClose,
  onCancelled,
}: {
  reservation: Reservation;
  quota: Quota | null;
  onClose: () => void;
  onCancelled: (response: CancellationResponse) => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<CancellationError | null>(null);

  async function confirm() {
    setSubmitting(true);
    setError(null);
    try {
      onCancelled(await cancelReservation(reservation.id));
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? (failure.problem as CancellationError)
          : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo cancelar la reserva." },
      );
      setSubmitting(false);
    }
  }

  return (
    <BottomSheet
      label="Cancelar la reserva"
      onClose={onClose}
      footer={
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={confirm}
            disabled={submitting}
            className="h-13 rounded-full text-lg font-semibold text-danger transition hover:bg-danger/5 active:bg-danger/10 disabled:cursor-wait disabled:opacity-70"
          >
            {submitting ? "Cancelando…" : "Sí, cancelar"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="h-11 rounded-full font-semibold text-gray-500 transition hover:bg-gray-100"
          >
            Volver
          </button>
        </div>
      }
    >
      <h2 className="text-2xl font-bold text-foreground sm:pr-8">¿Cancelar la reserva?</h2>

      <dl className="mt-6 flex flex-col gap-3 border-b border-gray-200 pb-4">
        <div className="flex items-baseline justify-between gap-4">
          <dt className="shrink-0 text-gray-500">Recurso</dt>
          <dd className="min-w-0 truncate font-semibold text-foreground">{reservation.name ?? reservation.resourceId}</dd>
        </div>
        <div className="flex items-baseline justify-between gap-4">
          <dt className="shrink-0 text-gray-500">{reservation.loan ? "Préstamo" : "Horario"}</dt>
          <dd className="text-right font-semibold text-foreground">{period(reservation)}</dd>
        </div>
      </dl>

      {reservation.loan && (
        <p className="mt-4 text-sm text-gray-500">Se libera el periodo completo del préstamo.</p>
      )}
      {quota && (
        <p className="mt-4 flex items-center gap-2 font-semibold text-green-700">
          <Check className="size-5 shrink-0" strokeWidth={2.5} />
          Recuperarás 1 de tus {quota.max} cupos.
        </p>
      )}

      {error && (
        <p role="alert" className="mt-4 flex gap-3 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-800">
          <TriangleAlert className="mt-0.5 size-5 shrink-0" />
          {error.detail}
        </p>
      )}
    </BottomSheet>
  );
}

/** "Hoy · 10:00 - 12:00", o para un préstamo "1 nov 10:00 → 6 nov 22:00". */
function period(reservation: Reservation): string {
  const startDay = dayOf(reservation.start);
  if (reservation.loan) {
    return `${shortDay(startDay)} ${timeOf(reservation.start)} → ${shortDay(dayOf(reservation.end))} ${timeOf(reservation.end)}`;
  }
  return `${shortDayLabel(startDay)} · ${timeOf(reservation.start)} - ${timeOf(reservation.end)}`;
}
