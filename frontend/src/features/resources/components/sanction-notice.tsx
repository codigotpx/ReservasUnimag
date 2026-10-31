import { ShieldAlert, TriangleAlert } from "lucide-react";
import type { SanctionNotice as Notice } from "../types";

/** El aviso de FR-016: la lista se muestra igual; la sanción decide al confirmar la reserva. */
export function SanctionNotice({ notice }: { notice: Notice }) {
  const active = notice.type === "ACTIVE_SANCTION";
  const Icon = active ? ShieldAlert : TriangleAlert;
  return (
    <section
      role="status"
      className={`flex gap-3 rounded-2xl px-5 py-4 text-sm ${active ? "bg-red-50 text-red-800" : "bg-amber-50 text-amber-800"}`}
    >
      <Icon className="size-5 shrink-0" />
      <div>
        <p>{notice.message}</p>
        {active && notice.reason && <p className="mt-1 font-semibold">Motivo: {notice.reason}</p>}
      </div>
    </section>
  );
}
