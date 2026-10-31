import { Clock } from "lucide-react";
import { dayInSentence, dayMonth, dayOf, timeOf } from "@/lib/dates";
import { codeLabel, statusLabel } from "../labels";
import type { DisplayedStatus, ResourceWithStatus, TimeSlot } from "../types";

const BADGE: Record<DisplayedStatus, string> = {
  DISPONIBLE: "bg-green-100 text-green-800",
  RESERVADO: "bg-blue-100 text-blue-800",
  EN_USO: "bg-amber-100 text-amber-800",
  BLOQUEO_ACADEMICO: "bg-violet-100 text-violet-800",
  EN_MANTENIMIENTO: "bg-red-100 text-red-700",
};

/** Un recurso con su estado (UC1 T039). Los no seleccionables se ven apagados y dicen por qué. */
export function ResourceCard({ resource, slot }: { resource: ResourceWithStatus; slot: TimeSlot }) {
  const muted = !resource.selectable;
  const badge =
    resource.status === "EN_USO" && resource.category === "ACTIVO"
      ? "bg-slate-200 text-slate-700"
      : BADGE[resource.status];

  return (
    <article className="rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <h3 className={`text-lg font-semibold leading-snug ${muted ? "text-gray-400" : "text-foreground"}`}>
          {resource.name}
        </h3>
        <span className={`shrink-0 rounded-lg px-3 py-1 text-sm font-semibold uppercase ${badge}`}>
          {statusLabel(resource.status, resource.category)}
        </span>
      </div>
      <p className={`mt-2 truncate ${muted ? "text-gray-400" : "text-gray-500"}`}>{details(resource)}</p>
      <p className={`mt-3 flex items-start gap-2 font-semibold ${muted ? "text-gray-400" : "text-brand-navy"}`}>
        <Clock className="mt-0.5 size-5 shrink-0" />
        <span>{footer(resource, slot)}</span>
      </p>
    </article>
  );
}

function details(resource: ResourceWithStatus): string {
  if (resource.category === "ACTIVO") {
    return [`Placa: ${resource.id}`, resource.location].join(" · ");
  }
  return [
    resource.maxCapacity !== undefined ? `Capacidad: ${resource.maxCapacity} pers.` : undefined,
    ...(resource.equipment ?? []).map(codeLabel),
    resource.location,
  ]
    .filter(Boolean)
    .join(" · ");
}

function footer(resource: ResourceWithStatus, slot: TimeSlot): string {
  if (resource.selectable) {
    const term = resource.loanTermBusinessDays;
    if (resource.category === "ACTIVO" && term !== undefined) {
      if (term === 0) return "Devolución: el mismo día";
      return `Devolución: ${term} ${term === 1 ? "día hábil" : "días hábiles"}`;
    }
    return `Disponible ${dayInSentence(slot.date)} de ${slot.start} a ${slot.end}`;
  }
  if (resource.occupiedUntil) {
    const verb = resource.status === "EN_USO" && resource.category === "ACTIVO" ? "Prestado" : "Ocupado";
    const day = dayOf(resource.occupiedUntil);
    return day === slot.date
      ? `${verb} hasta las ${timeOf(resource.occupiedUntil)}`
      : `${verb} hasta el ${dayMonth(day)}`;
  }
  return resource.reason ?? statusLabel(resource.status, resource.category);
}
