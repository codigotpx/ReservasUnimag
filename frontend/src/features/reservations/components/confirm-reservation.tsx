"use client";

import { Check, CircleCheck, Loader2, TriangleAlert, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { BottomSheet } from "@/components/bottom-sheet";
import { Dropdown } from "@/components/dropdown";
import { checkAvailability, type AvailabilityResponse } from "@/features/resources/api";
import { codeLabel, statusLabel } from "@/features/resources/labels";
import type { ResourceWithStatus, SanctionNotice, TimeSlot } from "@/features/resources/types";
import { ApiRequestError } from "@/lib/api/client";
import {
  HALF_HOURS,
  addMinutes,
  dayMonth,
  dayOf,
  durationLabel,
  fullDayLabel,
  minutesBetween,
  timeOf,
} from "@/lib/dates";
import { createReservation, fetchExpectedDueDate } from "../api";
import type { ExpectedDueDate, Quota, Reservation, ReservationError } from "../types";
import { DenialMessage } from "./denial-message";

/** El tope de una reserva de espacio (UC2 FR-009). */
const SPACE_LIMIT_MINUTES = 120;
const PICKUP_TIMES = HALF_HOURS.slice(0, -1);

type Check = "ok" | "fail" | "warn" | "loading";
type Availability = { key: string; data?: AvailabilityResponse; failed?: boolean };
type DueDate = { key: string; data?: ExpectedDueDate; failed?: string };

/**
 * Confirmar una reserva (UC2 T036 y T037). Revalida el recurso al abrir (UC8 T013) y muestra lo que el
 * backend va a comprobar; la decisión final es suya, al confirmar.
 */
export function ConfirmReservation({
  resource,
  slot,
  sanctionNotice,
  quota,
  onClose,
  onReserved,
  onStale,
}: {
  resource: ResourceWithStatus;
  slot: TimeSlot;
  sanctionNotice?: SanctionNotice;
  quota: Quota | null;
  onClose: () => void;
  onReserved: (reservation: Reservation) => void;
  /** La lista ya no refleja la realidad: el recurso resultó ocupado al confirmar. */
  onStale: () => void;
}) {
  const isAsset = resource.category === "ACTIVO";
  const [pickup, setPickup] = useState(PICKUP_TIMES.includes(slot.start) ? slot.start : PICKUP_TIMES[0]);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [dueDate, setDueDate] = useState<DueDate | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [denial, setDenial] = useState<ReservationError | null>(null);
  const [done, setDone] = useState<Reservation | null>(null);

  // Un activo se revisa en la media hora de la recogida; un espacio, en la franja completa.
  const checkSlot = isAsset ? { date: slot.date, start: pickup, end: addMinutes(pickup, 30) } : slot;
  const checkKey = `${checkSlot.date} ${checkSlot.start} ${checkSlot.end}`;

  useEffect(() => {
    let active = true;
    checkAvailability(resource.id, { date: slot.date, start: checkSlot.start, end: checkSlot.end })
      .then((data) => active && setAvailability({ key: checkKey, data }))
      .catch(() => active && setAvailability({ key: checkKey, failed: true }));
    return () => {
      active = false;
    };
  }, [resource.id, slot.date, checkSlot.start, checkSlot.end, checkKey]);

  useEffect(() => {
    if (!isAsset) return;
    let active = true;
    fetchExpectedDueDate(resource.id, slot.date, pickup)
      .then((data) => active && setDueDate({ key: pickup, data }))
      .catch(
        (error) =>
          active &&
          setDueDate({
            key: pickup,
            failed: error instanceof ApiRequestError ? error.problem.detail : "No se pudo calcular el vencimiento.",
          }),
      );
    return () => {
      active = false;
    };
  }, [isAsset, resource.id, slot.date, pickup]);

  const currentAvailability = availability?.key === checkKey ? availability : null;
  const currentDue = dueDate?.key === pickup ? dueDate : null;
  const minutes = minutesBetween(slot.start, slot.end);
  const tooLong = !isAsset && minutes > SPACE_LIMIT_MINUTES;

  const sanctionCheck: [Check, string] = !sanctionNotice
    ? ["ok", "Sin sanciones activas"]
    : sanctionNotice.type === "ACTIVE_SANCTION"
      ? ["fail", sanctionNotice.until ? `Sanción vigente hasta el ${dayMonth(sanctionNotice.until)}` : "Sanción vigente"]
      : ["warn", "No se pudo comprobar tu situación; se revisa al confirmar"];
  const quotaCheck: [Check, string] = !quota
    ? ["warn", "No se pudo leer tu cupo; se revisa al confirmar"]
    : quota.used < quota.max
      ? ["ok", `Cupo disponible (${quota.used} de ${quota.max})`]
      : ["fail", `Sin cupo (${quota.used} de ${quota.max})`];
  const freeCheck: [Check, string] = !currentAvailability
    ? ["loading", "Comprobando disponibilidad…"]
    : currentAvailability.failed
      ? ["warn", "No se pudo comprobar la disponibilidad; se revisa al confirmar"]
      : currentAvailability.data!.available
        ? ["ok", isAsset ? "Recurso libre para recoger" : "Recurso libre en la franja"]
        : ["fail", currentAvailability.data!.reason ?? "El recurso ya no está libre"];

  const checks = [sanctionCheck, quotaCheck, freeCheck];
  const blocked =
    tooLong ||
    checks.some(([state]) => state === "fail" || state === "loading") ||
    (isAsset && !currentDue?.data);

  async function confirm() {
    setSubmitting(true);
    setDenial(null);
    try {
      const reservation = await createReservation(
        isAsset
          ? { resourceId: resource.id, date: slot.date, pickup }
          : { resourceId: resource.id, date: slot.date, start: slot.start, end: slot.end },
      );
      setDone(reservation);
      onReserved(reservation);
    } catch (error) {
      const problem: ReservationError =
        error instanceof ApiRequestError
          ? error.problem
          : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo confirmar la reserva." };
      setDenial(problem);
      if (problem.code === "RES-001" || problem.code === "RES-004") onStale();
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <BottomSheet
        label="Reserva confirmada"
        onClose={onClose}
        footer={<PrimaryButton onClick={onClose}>Listo</PrimaryButton>}
      >
        <Confirmed reservation={done} name={resource.name} />
      </BottomSheet>
    );
  }

  const status = currentAvailability?.data?.status ?? resource.status;

  return (
    <BottomSheet
      label={`Reservar ${resource.name}`}
      onClose={onClose}
      footer={
        <>
          <p className="mb-3 text-center text-sm text-gray-500">La disponibilidad se vuelve a comprobar al confirmar.</p>
          <PrimaryButton onClick={confirm} disabled={blocked || submitting}>
            {submitting ? "Confirmando…" : "Confirmar reserva"}
          </PrimaryButton>
        </>
      }
    >
      <div className="flex items-start justify-between gap-3 sm:pr-8">
        <h2 className="text-2xl font-bold leading-tight text-foreground">{resource.name}</h2>
        <span
          className={`shrink-0 rounded-lg px-3 py-1 text-sm font-semibold uppercase ${
            status === "DISPONIBLE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
          }`}
        >
          {statusLabel(status, resource.category)}
        </span>
      </div>
      <p className="mt-1 text-gray-500">{codeLabel(resource.type)}</p>

      <dl className="mt-5 flex flex-col gap-3.5 rounded-3xl bg-gray-100 px-5 py-5">
        <Row label="Fecha">{fullDayLabel(slot.date)}</Row>
        {isAsset ? (
          <>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-gray-500">Recogida</dt>
              <dd className="w-32">
                <Dropdown
                  label="Hora de recogida"
                  value={pickup}
                  onChange={setPickup}
                  options={PICKUP_TIMES.map((time) => ({ value: time, label: time }))}
                />
              </dd>
            </div>
            <Row label="Devolución">
              {currentDue?.data ? (
                `${fullDayLabel(dayOf(currentDue.data.dueAt))}, ${timeOf(currentDue.data.dueAt)}`
              ) : currentDue?.failed ? (
                <span className="font-normal text-red-700">{currentDue.failed}</span>
              ) : (
                <Loader2 aria-label="Calculando" className="ml-auto size-5 animate-spin text-gray-400" />
              )}
            </Row>
            {currentDue?.data && (
              <Row label="Plazo">
                {currentDue.data.termBusinessDays === 0
                  ? "Uso en sitio, el mismo día"
                  : `${currentDue.data.termBusinessDays} ${currentDue.data.termBusinessDays === 1 ? "día hábil" : "días hábiles"}`}
              </Row>
            )}
            <Row label="Placa">{resource.id}</Row>
            <Row label="Ubicación">{resource.location}</Row>
            {resource.condition && <Row label="Estado">{codeLabel(resource.condition)}</Row>}
          </>
        ) : (
          <>
            <Row label="Hora">
              {slot.start} - {slot.end}
            </Row>
            <div>
              <Row label="Duración">{durationLabel(minutes)}</Row>
              <div className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-gray-200">
                <div
                  className={`h-full rounded-full ${tooLong ? "bg-red-600" : "bg-brand-navy"}`}
                  style={{ width: `${Math.min(100, (minutes / SPACE_LIMIT_MINUTES) * 100)}%` }}
                />
              </div>
            </div>
            {resource.maxCapacity !== undefined && <Row label="Aforo">{resource.maxCapacity} personas</Row>}
            <Row label="Ubicación">{resource.location}</Row>
            {resource.faculty && <Row label="Facultad">{resource.faculty}</Row>}
            {resource.equipment && resource.equipment.length > 0 && (
              <Row label="Equipamiento">{resource.equipment.map(codeLabel).join(", ")}</Row>
            )}
          </>
        )}
      </dl>

      {tooLong && (
        <p role="alert" className="mt-4 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-800">
          Una reserva de espacio puede durar como máximo 2 horas. Cambia la franja en la búsqueda.
        </p>
      )}

      <ul className="mt-5 flex flex-col gap-3">
        {checks.map(([state, text]) => (
          <li key={text} className="flex items-start gap-3 text-base text-foreground">
            <CheckIcon state={state} />
            <span className={state === "fail" ? "text-red-700" : ""}>{text}</span>
          </li>
        ))}
      </ul>

      {denial && (
        <div className="mt-5">
          <DenialMessage error={denial} />
        </div>
      )}
    </BottomSheet>
  );
}

function Confirmed({ reservation, name }: { reservation: Reservation; name: string }) {
  return (
    <div className="flex flex-col items-center py-4 text-center">
      <CircleCheck className="size-16 text-green-600" strokeWidth={1.5} />
      <h2 className="mt-4 text-2xl font-bold text-foreground">Reserva confirmada</h2>
      <p className="mt-2 text-gray-600">{reservation.name ?? name}</p>
      <p className="mt-4 rounded-2xl bg-gray-100 px-5 py-4 text-foreground">
        {reservation.loan ? (
          <>
            Recógelo el <strong>{dayMonth(dayOf(reservation.loan.pickup))}</strong> a las{" "}
            <strong>{timeOf(reservation.loan.pickup)}</strong> y devuélvelo antes del{" "}
            <strong>{dayMonth(dayOf(reservation.loan.dueAt))}</strong> a las{" "}
            <strong>{timeOf(reservation.loan.dueAt)}</strong>.
          </>
        ) : (
          <>
            <strong>{fullDayLabel(dayOf(reservation.start))}</strong>, de <strong>{timeOf(reservation.start)}</strong> a{" "}
            <strong>{timeOf(reservation.end)}</strong>.
          </>
        )}
      </p>
      {reservation.quota && (
        <p className="mt-3 text-sm text-gray-500">
          Ahora tienes {reservation.quota.used} de {reservation.quota.max} reservas vigentes.
        </p>
      )}
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="shrink-0 text-gray-500">{label}</dt>
      <dd className="text-right font-semibold text-foreground">{children}</dd>
    </div>
  );
}

function CheckIcon({ state }: { state: Check }) {
  const className = "mt-0.5 size-5 shrink-0";
  switch (state) {
    case "ok":
      return <Check aria-label="Cumple" className={`${className} text-green-600`} strokeWidth={2.5} />;
    case "fail":
      return <X aria-label="No cumple" className={`${className} text-red-600`} strokeWidth={2.5} />;
    case "warn":
      return <TriangleAlert aria-label="Sin comprobar" className={`${className} text-amber-500`} />;
    case "loading":
      return <Loader2 aria-label="Comprobando" className={`${className} animate-spin text-gray-400`} />;
  }
}

function PrimaryButton({
  onClick,
  disabled,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="h-13 w-full rounded-full bg-brand-navy text-lg font-semibold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-gray-300"
    >
      {children}
    </button>
  );
}
