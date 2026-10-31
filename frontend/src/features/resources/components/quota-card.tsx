"use client";

import { Info } from "lucide-react";
import { useState } from "react";
import type { Quota } from "@/features/reservations/types";

/**
 * "Tienes 2 de 3 reservas vigentes": el cupo de UC2 FR-008, que se comparte entre espacios y activos.
 * Quien la usa no la muestra si el cupo no se pudo leer: es información de apoyo, no bloquea la consulta.
 */
export function QuotaCard({ quota }: { quota: Quota }) {
  const [helpOpen, setHelpOpen] = useState(false);

  return (
    <section className="rounded-2xl border border-gray-200 bg-white px-5 py-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-gray-500">
          Tienes{" "}
          <strong className="font-bold text-brand-navy">
            {quota.used} de {quota.max}
          </strong>{" "}
          reservas vigentes
        </p>
        <button
          type="button"
          aria-label="Qué es el cupo de reservas"
          aria-expanded={helpOpen}
          onClick={() => setHelpOpen((open) => !open)}
          className="rounded-full p-1 text-brand-navy transition hover:bg-gray-100"
        >
          <Info className="size-6" />
        </button>
      </div>
      <div className="mt-3 flex gap-1.5" aria-hidden>
        {Array.from({ length: quota.max }, (_, index) => (
          <span
            key={index}
            className={`h-2 flex-1 rounded-full ${index < quota.used ? "bg-brand-navy" : "bg-gray-200"}`}
          />
        ))}
      </div>
      {helpOpen && (
        <p className="mt-3 text-sm text-gray-500">
          Puedes tener hasta {quota.max} reservas vigentes al mismo tiempo, sumando espacios y activos. Un cupo se
          libera cuando la reserva termina, la cancelas o devuelves el activo.
        </p>
      )}
    </section>
  );
}
