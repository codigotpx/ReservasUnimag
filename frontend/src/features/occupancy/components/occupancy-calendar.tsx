"use client";

import { CalendarDays, ChevronLeft, ChevronRight, RefreshCw, TriangleAlert, Wrench } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Dropdown } from "@/components/dropdown";
import type { ResourceWithStatus } from "@/features/resources/types";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { addDays, dayOf, minutesOfDay, mondayOf, shortDay, timeOf, today } from "@/lib/dates";
import { fetchOccupancy, fetchSpaces } from "../api";
import type { OccupancyResponse, ScheduledUse, UseKind } from "../types";

/** De lunes a sábado: los domingos no hay clases ni se reserva. */
const DAYS = 6;
const DAY_NAMES = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];
const OPENING = 6 * 60;
const CLOSING = 22 * 60;
const HOUR_HEIGHT = 48;

const KINDS: Record<UseKind, { label: string; className: string }> = {
  CLASS: { label: "Clase", className: "bg-[#2d0e8c] text-white" },
  EXTRAORDINARY: { label: "Actividad extraordinaria", className: "bg-[#6b3fd4] text-white" },
  STUDENT_RESERVATION: { label: "Reserva estudiantil", className: "bg-brand-navy text-white" },
};

type Outcome = { key: string; data: OccupancyResponse } | { key: string; error: ApiError };

/**
 * Ocupación de espacios (diseño admin-ocupacion): la semana de un espacio con sus clases, actividades extraordinarias y
 * reservas. Es una foto de lo confirmado; la disponibilidad de una franja la sigue respondiendo UC8.
 */
export function OccupancyCalendar() {
  const router = useRouter();
  const [spaces, setSpaces] = useState<ResourceWithStatus[] | null>(null);
  const [spacesError, setSpacesError] = useState<string | null>(null);
  const [resourceId, setResourceId] = useState<string | null>(null);
  const [monday, setMonday] = useState(() => mondayOf(today()));
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  useEffect(() => {
    let active = true;
    fetchSpaces(today())
      .then((list) => {
        if (!active) return;
        setSpaces(list);
        setResourceId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((error) => {
        if (!active) return;
        if (error instanceof ApiRequestError && error.problem.status === 401) {
          router.replace("/login");
          return;
        }
        setSpacesError(error instanceof ApiRequestError ? error.problem.detail : "No se pudo cargar el inventario.");
      });
    return () => {
      active = false;
    };
  }, [router]);

  const saturday = addDays(monday, DAYS - 1);
  const key = `${resourceId}|${monday}|${attempt}`;

  useEffect(() => {
    if (!resourceId) return;
    let active = true;
    fetchOccupancy(resourceId, monday, saturday)
      .then((data) => active && setOutcome({ key, data }))
      .catch(
        (error) =>
          active &&
          setOutcome({
            key,
            error:
              error instanceof ApiRequestError
                ? error.problem
                : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo cargar la ocupación." },
          }),
      );
    return () => {
      active = false;
    };
  }, [resourceId, monday, saturday, key]);

  const current = outcome?.key === key ? outcome : null;
  const days = Array.from({ length: DAYS }, (_, index) => addDays(monday, index));

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <section className="flex flex-wrap items-center gap-4 rounded-3xl border border-gray-200 bg-white p-4">
        <div className="w-72">
          {spaces && spaces.length > 0 && resourceId ? (
            <Dropdown
              label="Espacio"
              value={resourceId}
              onChange={setResourceId}
              options={spaces.map((space) => ({ value: space.id, label: `Espacio: ${space.name}` }))}
             
            />
          ) : (
            <div className="h-12 animate-pulse rounded-full bg-gray-50" />
          )}
        </div>
        <div className="flex items-center gap-1 rounded-full border border-gray-200">
          <button
            type="button"
            aria-label="Semana anterior"
            onClick={() => setMonday(addDays(monday, -7))}
            className="p-3 text-brand-navy transition hover:bg-gray-50"
          >
            <ChevronLeft className="size-4" />
          </button>
          <span className="flex items-center gap-2 px-2 text-sm font-medium text-foreground">
            <CalendarDays className="size-4 text-gray-500" />
            Semana del {shortDay(monday)} al {shortDay(saturday)} de {monday.slice(0, 4)}
          </span>
          <button
            type="button"
            aria-label="Semana siguiente"
            onClick={() => setMonday(addDays(monday, 7))}
            className="p-3 text-brand-navy transition hover:bg-gray-50"
          >
            <ChevronRight className="size-4" />
          </button>
        </div>
        <button
          type="button"
          onClick={() => setMonday(mondayOf(today()))}
          className="rounded-full border border-gray-200 px-4 py-2.5 text-sm font-bold text-brand-navy transition hover:bg-gray-50"
        >
          Esta semana
        </button>
        <ul className="ml-auto flex flex-wrap gap-4 text-sm text-gray-600">
          {Object.values(KINDS).map((kind) => (
            <li key={kind.label} className="flex items-center gap-2">
              <span className={`size-4 rounded ${kind.className}`} />
              {kind.label}
            </li>
          ))}
        </ul>
      </section>

      {spacesError ? (
        <Problem detail={spacesError} onRetry={() => window.location.reload()} />
      ) : current && "error" in current ? (
        <Problem detail={current.error.detail} onRetry={() => setAttempt((value) => value + 1)} />
      ) : (
        <>
          {current?.data.operationalStatus === "EN_MANTENIMIENTO" && (
            <p className="flex items-center gap-3 rounded-2xl border border-[#c5cdd5] bg-[#e8edf2] p-4 text-sm text-foreground">
              <Wrench className="size-5 text-gray-600" />
              Según el inventario, este espacio está en mantenimiento ahora: no se puede reservar mientras tanto.
            </p>
          )}
          <Grid days={days} uses={current?.data.uses ?? []} loading={!current} />
        </>
      )}
    </div>
  );
}

function Grid({ days, uses, loading }: { days: string[]; uses: ScheduledUse[]; loading: boolean }) {
  const hours = Array.from({ length: (CLOSING - OPENING) / 60 }, (_, index) => OPENING / 60 + index);
  const todayDate = today();
  return (
    <div aria-busy={loading} className="overflow-x-auto rounded-3xl border border-gray-200 bg-white">
      <div className="grid min-w-[60rem]" style={{ gridTemplateColumns: `5rem repeat(${days.length}, 1fr)` }}>
        <div className="border-b border-gray-200" />
        {days.map((day, index) => (
          <div
            key={day}
            className={`border-b border-l border-gray-200 py-3 text-center text-sm font-bold ${
              day === todayDate ? "text-brand-navy" : "text-foreground"
            }`}
          >
            {DAY_NAMES[index]} {Number(day.slice(8))}
          </div>
        ))}

        <div className="relative" style={{ height: hours.length * HOUR_HEIGHT }}>
          {hours.map((hour, index) => (
            <span
              key={hour}
              className="absolute right-3 -translate-y-1/2 text-xs font-medium text-gray-500"
              style={{ top: index * HOUR_HEIGHT + (index === 0 ? 10 : 0) }}
            >
              {String(hour).padStart(2, "0")}:00
            </span>
          ))}
        </div>
        {days.map((day) => (
          <div
            key={day}
            className={`relative border-l border-gray-200 ${loading ? "animate-pulse bg-gray-50" : ""}`}
            style={{
              height: hours.length * HOUR_HEIGHT,
              backgroundImage: `repeating-linear-gradient(to bottom, transparent 0, transparent ${HOUR_HEIGHT - 1}px, #eef1f4 ${HOUR_HEIGHT - 1}px, #eef1f4 ${HOUR_HEIGHT}px)`,
            }}
          >
            {uses
              .filter((use) => dayOf(use.start) === day)
              .map((use) => (
                <UseBlock key={`${use.start}-${use.kind}`} use={use} />
              ))}
          </div>
        ))}
      </div>
      {!loading && uses.length === 0 && (
        <p className="border-t border-gray-200 p-4 text-center text-sm text-gray-500">
          Esta semana el espacio no tiene clases ni reservas confirmadas.
        </p>
      )}
    </div>
  );
}

function UseBlock({ use }: { use: ScheduledUse }) {
  const start = Math.max(minutesOfDay(use.start), OPENING);
  const end = Math.min(minutesOfDay(use.end) || CLOSING, CLOSING);
  const kind = KINDS[use.kind];
  const top = ((start - OPENING) / 60) * HOUR_HEIGHT;
  const height = Math.max(((end - start) / 60) * HOUR_HEIGHT - 4, 20);
  return (
    <div
      title={`${use.course ?? kind.label}, de ${timeOf(use.start)} a ${timeOf(use.end)}`}
      className={`absolute inset-x-1 overflow-hidden rounded-full px-2.5 py-1.5 text-xs shadow-sm ${kind.className}`}
      style={{ top: top + 2, height }}
    >
      <p className="truncate font-bold">{use.course ?? kind.label}</p>
      <p className="truncate opacity-80">
        {use.program ? `${use.program}, ` : ""}
        {timeOf(use.start)} - {timeOf(use.end)}
      </p>
    </div>
  );
}

function Problem({ detail, onRetry }: { detail: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-3xl border border-gray-200 bg-white py-10 text-center">
      <TriangleAlert className="size-9 text-amber-500" />
      <p className="max-w-sm text-gray-600">{detail}</p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-5 py-2.5 text-sm font-semibold text-white"
      >
        <RefreshCw className="size-4" /> Reintentar
      </button>
    </div>
  );
}
