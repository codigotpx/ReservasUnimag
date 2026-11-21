"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";

/**
 * El identificador de la reserva, con botón para copiarlo. Es el que usa el Módulo 3 en sus reportes de no asistencia
 * y en los check-outs, así que tiene que poder leerse y pegarse entero.
 */
export function ReservationId({ id, label = "ID de reserva" }: { id: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(id);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Sin permiso de portapapeles: el texto sigue a la vista para copiarlo a mano
    }
  }

  return (
    <p className="mt-2 flex items-center gap-2 text-xs text-gray-500">
      {label && <span className="shrink-0">{label}:</span>}
      <code className="min-w-0 break-all font-mono text-gray-600">{id}</code>
      <button
        type="button"
        onClick={copy}
        aria-label={copied ? "Copiado" : "Copiar el ID de la reserva"}
        title={copied ? "Copiado" : "Copiar"}
        className="shrink-0 rounded-md p-1 text-gray-400 transition hover:bg-gray-100 hover:text-brand-navy"
      >
        {copied ? <Check className="size-4 text-green-700" /> : <Copy className="size-4" />}
      </button>
    </p>
  );
}
