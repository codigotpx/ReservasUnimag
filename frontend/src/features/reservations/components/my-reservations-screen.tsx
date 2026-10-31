"use client";

import { CalendarX2, Plus, RefreshCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { QuotaCard } from "@/features/resources/components/quota-card";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { fetchMyReservations } from "../api";
import type { CancellationResponse, Quota, Reservation } from "../types";
import { ActiveReservationCard } from "./active-reservation-card";
import { CancelReservation } from "./cancel-reservation";
import { HistoryList } from "./history-list";

type Tab = "vigentes" | "historial";
type Loaded = { reservations: Reservation[]; quota: Quota; catalogUnavailable: boolean };
type Outcome = { key: string; data: Loaded } | { key: string; error: ApiError };

/** Mis reservas (UC2 §3, UC4 T020 y UC9 T020): las vigentes con su cupo, y el historial. */
export function MyReservationsScreen() {
  const router = useRouter();
  const query = useSearchParams();
  const tab: Tab = query.get("tab") === "historial" ? "historial" : "vigentes";

  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [cancelling, setCancelling] = useState<Reservation | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const requestKey = `${tab}#${attempt}`;

  // "Empieza en…" se mueve solo: se recalcula cada minuto.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    load(tab)
      .then((data) => active && setOutcome({ key: requestKey, data }))
      .catch((error) => {
        if (!active) return;
        const problem: ApiError =
          error instanceof ApiRequestError
            ? error.problem
            : { type: "about:blank", title: "Error", status: 0, detail: "No se pudieron cargar tus reservas." };
        if (problem.status === 401) {
          router.replace("/login");
          return;
        }
        setOutcome({ key: requestKey, error: problem });
      });
    return () => {
      active = false;
    };
  }, [tab, requestKey, router]);

  const current = outcome?.key === requestKey ? outcome : null;

  function afterCancel(response: CancellationResponse) {
    setCancelling(null);
    setNotice(
      response.alreadyCancelled ? "Esa reserva ya estaba cancelada." : "Reserva cancelada. El recurso quedó libre.",
    );
    setAttempt((value) => value + 1);
  }

  return (
    <>
      {/* En el celular las pestañas ocupan media pantalla cada una; en escritorio van a la izquierda, alineadas con el título. */}
      <div className="sticky top-16 z-10 border-b border-gray-200 bg-white lg:top-20">
        <div
          role="tablist"
          aria-label="Mis reservas"
          className="mx-auto grid w-full grid-cols-2 lg:flex lg:max-w-6xl lg:gap-8 lg:px-10"
        >
          {(["vigentes", "historial"] as const).map((name) => (
            <button
              key={name}
              type="button"
              role="tab"
              aria-selected={tab === name}
              onClick={() => {
                setNotice(null);
                router.replace(name === "vigentes" ? "/my-reservations" : "/my-reservations?tab=historial");
              }}
              className={`-mb-px h-14 border-b-[3px] text-base font-semibold transition lg:px-1 ${
                tab === name ? "border-brand-navy text-brand-navy" : "border-transparent text-gray-500 hover:text-gray-700"
              }`}
            >
              {name === "vigentes" ? "Vigentes" : "Historial"}
            </button>
          ))}
        </div>
      </div>

      <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-5 px-4 py-5 sm:px-6 lg:max-w-6xl lg:px-10 lg:py-8">
        {notice && (
          <p role="status" className="rounded-2xl bg-green-50 px-4 py-3 text-sm font-medium text-green-800">
            {notice}
          </p>
        )}

        {!current ? (
          <Skeleton />
        ) : "error" in current ? (
          current.error.status === 403 ? (
            <Empty text="Tu rol no hace reservas, así que aquí no hay nada que mostrar." />
          ) : (
            <Problem detail={current.error.detail} onRetry={() => setAttempt((value) => value + 1)} />
          )
        ) : (
          <>
            {current.data.catalogUnavailable && (
              <p className="flex gap-3 rounded-2xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
                <TriangleAlert className="size-5 shrink-0" />
                El inventario no respondió, así que algunos recursos aparecen con su código en vez del nombre.
              </p>
            )}
            {tab === "vigentes" ? (
              <Active
                data={current.data}
                now={now}
                onCancel={(reservation) => {
                  setNotice(null);
                  setCancelling(reservation);
                }}
              />
            ) : current.data.reservations.length === 0 ? (
              <Empty text="Todavía no tienes reservas pasadas." />
            ) : (
              <HistoryList reservations={current.data.reservations} />
            )}
          </>
        )}
      </div>

      {cancelling && current && "data" in current && (
        <CancelReservation
          reservation={cancelling}
          quota={current.data.quota}
          onClose={() => setCancelling(null)}
          onCancelled={afterCancel}
        />
      )}
    </>
  );
}

/** Vigentes: GET ?vigentes=true. Historial: todas menos las vigentes, porque qué es vigente lo decide el backend. */
async function load(tab: Tab): Promise<Loaded> {
  const active = await fetchMyReservations(true);
  if (tab === "vigentes") return active;
  const all = await fetchMyReservations(false);
  const activeIds = new Set(active.reservations.map((reservation) => reservation.id));
  return { ...all, reservations: all.reservations.filter((reservation) => !activeIds.has(reservation.id)) };
}

function Active({
  data,
  now,
  onCancel,
}: {
  data: Loaded;
  now: number;
  onCancel: (reservation: Reservation) => void;
}) {
  const spaces = data.reservations.filter((reservation) => reservation.category === "ESPACIO");
  const assets = data.reservations.filter((reservation) => reservation.category === "ACTIVO");
  // El cupo es el resumen de la pantalla: arriba y a todo el ancho; en escritorio, en una sola fila con la acción.
  return (
    <div className="flex flex-col gap-5 lg:gap-8">
      <QuotaCard
        quota={data.quota}
        wide
        action={
          data.quota.used < data.quota.max && (
            <Link
              href="/resources"
              className="inline-flex h-11 items-center gap-2 rounded-full bg-brand-navy px-5 font-semibold text-white transition hover:bg-brand-navy/90"
            >
              <Plus className="size-5" /> Nueva reserva
            </Link>
          )
        }
      />
      <div className="flex min-w-0 flex-col gap-5 lg:gap-8">
        {data.reservations.length === 0 && (
          <Empty text="No tienes reservas vigentes.">
            <Link href="/resources" className="mt-2 rounded-full bg-brand-navy px-5 py-2.5 font-semibold text-white">
              Buscar recursos
            </Link>
          </Empty>
        )}
        {[
          { title: "Espacios", items: spaces },
          { title: "Activos en préstamo", items: assets },
        ].map(
          ({ title, items }) =>
            items.length > 0 && (
              <section key={title} className="flex flex-col gap-4">
                <h2 className="pt-2 font-semibold text-gray-500 lg:pt-0">{title}</h2>
                <div className="grid gap-4 lg:grid-cols-2">
                  {items.map((reservation) => (
                    <ActiveReservationCard
                      key={reservation.id}
                      reservation={reservation}
                      now={now}
                      onCancel={() => onCancel(reservation)}
                    />
                  ))}
                </div>
              </section>
            ),
        )}
      </div>
    </div>
  );
}

function Empty({ text, children }: { text: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl border border-gray-200 bg-white px-6 py-12 text-center text-gray-500">
      <CalendarX2 className="size-10 text-gray-400" />
      <p className="max-w-sm">{text}</p>
      {children}
    </div>
  );
}

function Problem({ detail, onRetry }: { detail: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-3xl border border-gray-200 bg-white px-6 py-12 text-center">
      <TriangleAlert className="size-10 text-amber-500" />
      <p className="max-w-sm text-gray-600">{detail}</p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-5 py-2.5 font-semibold text-white"
      >
        <RefreshCw className="size-4" /> Reintentar
      </button>
    </div>
  );
}

function Skeleton() {
  return (
    <div aria-busy="true" aria-label="Cargando reservas" className="grid gap-4 lg:grid-cols-2">
      {[0, 1].map((card) => (
        <div key={card} className="h-44 animate-pulse rounded-3xl border border-gray-200 bg-white" />
      ))}
    </div>
  );
}
