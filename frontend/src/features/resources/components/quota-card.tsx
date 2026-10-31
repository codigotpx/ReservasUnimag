"use client";

import { Info } from "lucide-react";
import { useState, type ReactNode } from "react";
import type { Quota } from "@/features/reservations/types";

/**
 * "Tienes 2 de 3 reservas vigentes": el cupo de UC2 FR-008, que se comparte entre espacios y activos.
 * Quien la usa no la muestra si el cupo no se pudo leer: es información de apoyo, no bloquea la consulta.
 * Con wide, desde lg el texto, la barra y la ayuda van en una sola fila, con lugar para una acción al final.
 */
export function QuotaCard({ quota, wide = false, action }: { quota: Quota; wide?: boolean; action?: ReactNode }) {
  const [helpOpen, setHelpOpen] = useState(false);

  return (
    <section className={`rounded-2xl border border-gray-200 bg-white px-5 py-4 ${wide ? "lg:px-6" : ""}`}>
      <div className={`flex flex-wrap items-center gap-x-3 gap-y-3 ${wide ? "lg:flex-nowrap lg:gap-x-6" : ""}`}>
        <p className="text-gray-500">
          Tienes{" "}
          <strong className="font-bold text-brand-navy">
            {quota.used} de {quota.max}
          </strong>{" "}
          reservas vigentes
        </p>
        {/* En la versión angosta la barra baja a su propia línea; en la ancha queda entre el texto y la ayuda. */}
        <div
          aria-hidden
          className={`order-last flex basis-full gap-1.5 ${wide ? "lg:order-none lg:max-w-sm lg:flex-1 lg:basis-auto" : ""}`}
        >
          {Array.from({ length: quota.max }, (_, index) => (
            <span
              key={index}
              className={`h-2 flex-1 rounded-full ${index < quota.used ? "bg-brand-navy" : "bg-gray-200"}`}
            />
          ))}
        </div>
        <button
          type="button"
          aria-label="Qué es el cupo de reservas"
          aria-expanded={helpOpen}
          onClick={() => setHelpOpen((open) => !open)}
          className={`ml-auto rounded-full p-1 text-brand-navy transition hover:bg-gray-100 ${wide ? "lg:ml-0" : ""}`}
        >
          <Info className="size-6" />
        </button>
        {action && <div className="hidden shrink-0 lg:ml-auto lg:block">{action}</div>}
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
