"use client";

import { ChevronLeft, ChevronRight, Info, PackageCheck, RefreshCw, TriangleAlert } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { dateTimeLabel, dayOf, shortDay, timeOf } from "@/lib/dates";
import { fetchPendingReturns, fetchPendingReviews } from "../api";
import type { Pagination, PendingReturnsResponse, PendingReviewsResponse } from "../types";

type Tab = "returns" | "reviews";
type Origin = "ESTUDIANTIL" | "ACADEMICO" | undefined;

/** Un préstamo está "cerca de perderse" si le quedan menos de 48 h para el umbral (UC12 Contratos §3). */
const NEAR_LOSS_MS = 48 * 60 * 60 * 1000;

/**
 * Pendientes de check-out (UC12 T021 y T022): los préstamos que no se han devuelto, con los que están cerca de
 * declararse perdidos, y los espacios terminados sin revisar, que es opcional. El check-out lo registra el Módulo 3;
 * aquí solo se consulta.
 */
export function PendingCheckOuts() {
  const [tab, setTab] = useState<Tab>("returns");
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <div role="tablist" aria-label="Pendientes" className="flex gap-2 border-b border-[#dde3e9]">
        {(
          [
            ["returns", "Devoluciones pendientes"],
            ["reviews", "Espacios sin revisar"],
          ] as [Tab, string][]
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            onClick={() => setTab(value)}
            className={`-mb-px border-b-[3px] px-4 py-3 text-sm font-bold transition ${
              tab === value ? "border-brand-navy text-brand-navy" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "returns" ? <PendingReturns /> : <PendingReviews />}
    </div>
  );
}

function PendingReturns() {
  const [page, setPage] = useState(1);
  const state = usePaged(`returns-${page}`, () => fetchPendingReturns(page));
  const [now] = useState(() => Date.now());

  return (
    <Loaded state={state}>
      {(data: PendingReturnsResponse) => (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="Sin devolver" value={data.summary.total} />
            <Stat label="Vencidos" value={data.summary.overdue} tone={data.summary.overdue > 0 ? "amber" : "muted"} />
            <Stat
              label="A menos de 48 h de perderse"
              value={data.summary.nearLossThreshold}
              tone={data.summary.nearLossThreshold > 0 ? "red" : "muted"}
            />
          </div>
          {data.loans.length === 0 ? (
            <Empty text="No hay préstamos pendientes de devolución." />
          ) : (
            <Table
              head={["Activo", "Persona", "Recogido", "Vence", "Atraso", "Se declara perdido"]}
              rows={data.loans.map((loan) => {
                const near =
                  loan.lossThresholdAt !== undefined && Date.parse(loan.lossThresholdAt) - now < NEAR_LOSS_MS;
                return {
                  key: loan.reservationId,
                  cells: [
                    <Named key="a" name={loan.resourceName ?? loan.resourceId} code={loan.resourceId} />,
                    loan.holder ? <Named key="p" name={loan.holder.name ?? loan.holder.code} code={loan.holder.code} /> : "—",
                    dateTimeLabel(loan.pickup),
                    dateTimeLabel(loan.dueAt),
                    loan.overdueDays > 0 ? (
                      <Badge key="o" tone="amber">
                        {loan.overdueDays} {loan.overdueDays === 1 ? "día" : "días"}
                      </Badge>
                    ) : (
                      <Badge key="o" tone="muted">
                        Al día
                      </Badge>
                    ),
                    loan.lossThresholdAt ? (
                      <span key="l" className={near ? "font-bold text-red-700" : "text-gray-600"}>
                        {dateTimeLabel(loan.lossThresholdAt)}
                        {near && <span className="block text-xs">Menos de 48 h</span>}
                      </span>
                    ) : (
                      "—"
                    ),
                  ],
                };
              })}
            />
          )}
          <Pager pagination={data.pagination} onPage={setPage} />
        </>
      )}
    </Loaded>
  );
}

function PendingReviews() {
  const [page, setPage] = useState(1);
  const [origin, setOrigin] = useState<Origin>(undefined);
  const state = usePaged(`reviews-${origin ?? "all"}-${page}`, () => fetchPendingReviews(page, origin));

  return (
    <>
      <p className="flex gap-3 rounded-lg border border-[#dde3e9] bg-white p-4 text-sm text-gray-600">
        <Info className="mt-0.5 size-5 shrink-0 text-brand-navy" />
        La revisión de espacios es opcional: la franja se liberó sola al terminar la reserva. Un check-out de espacio
        solo deja constancia de cómo quedó, y lo registra el Módulo 3.
      </p>
      <div className="flex flex-wrap gap-2">
        {(
          [
            [undefined, "Todas"],
            ["ESTUDIANTIL", "Estudiantiles"],
            ["ACADEMICO", "Académicas"],
          ] as [Origin, string][]
        ).map(([value, label]) => (
          <button
            key={label}
            type="button"
            aria-pressed={origin === value}
            onClick={() => {
              setOrigin(value);
              setPage(1);
            }}
            className={`rounded-full border px-4 py-1.5 text-sm font-semibold transition ${
              origin === value
                ? "border-brand-navy bg-brand-navy text-white"
                : "border-[#dde3e9] bg-white text-gray-600 hover:border-gray-300"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      <Loaded state={state}>
        {(data: PendingReviewsResponse) => (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat label="Sin revisar" value={data.summary.total} />
              <Stat label="Estudiantiles" value={data.summary.student} tone="muted" />
              <Stat label="Académicas" value={data.summary.academic} tone="muted" />
            </div>
            {data.reservations.length === 0 ? (
              <Empty text="No hay espacios pendientes de revisión." />
            ) : (
              <Table
                head={["Espacio", "Origen", "Quién la tenía", "Franja", "Terminó"]}
                rows={data.reservations.map((review) => ({
                  key: review.reservationId,
                  cells: [
                    <Named key="e" name={review.resourceName ?? review.resourceId} code={review.resourceId} />,
                    <Badge key="o" tone={review.origin === "ACADEMICO" ? "violet" : "muted"}>
                      {review.origin === "ACADEMICO" ? "Académica" : "Estudiantil"}
                    </Badge>,
                    review.holder ? (
                      <Named key="h" name={review.holder.name ?? review.holder.code} code={review.holder.code} />
                    ) : (
                      "Clase"
                    ),
                    `${shortDay(dayOf(review.start))}, ${timeOf(review.start)} - ${timeOf(review.end)}`,
                    review.finishedDaysAgo === 0
                      ? "Hoy"
                      : `Hace ${review.finishedDaysAgo} ${review.finishedDaysAgo === 1 ? "día" : "días"}`,
                  ],
                }))}
              />
            )}
            <Pager pagination={data.pagination} onPage={setPage} />
          </>
        )}
      </Loaded>
    </>
  );
}

type Paged<T> = { key: string; data: T } | { key: string; error: ApiError } | null;

/** Pide una página y la deja asociada a su clave, para no mostrar la anterior mientras llega la nueva. */
function usePaged<T>(key: string, load: () => Promise<T>): { current: Paged<T>; retry: () => void } {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Paged<T>>(null);
  const requestKey = `${key}#${attempt}`;

  useEffect(() => {
    let active = true;
    load()
      .then((data) => active && setOutcome({ key: requestKey, data }))
      .catch((error) => {
        if (!active) return;
        const problem: ApiError =
          error instanceof ApiRequestError
            ? error.problem
            : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo cargar la lista." };
        if (problem.status === 401) {
          router.replace("/login");
          return;
        }
        setOutcome({ key: requestKey, error: problem });
      });
    return () => {
      active = false;
    };
    // load cambia en cada render; la clave dice cuándo hay que volver a pedir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey, router]);

  return {
    current: outcome?.key === requestKey ? outcome : null,
    retry: () => setAttempt((value) => value + 1),
  };
}

function Loaded<T>({
  state,
  children,
}: {
  state: { current: Paged<T>; retry: () => void };
  children: (data: T) => ReactNode;
}) {
  const { current, retry } = state;
  if (!current) {
    return (
      <div aria-busy="true" className="flex flex-col gap-2">
        {[0, 1, 2].map((index) => (
          <div key={index} className="h-14 animate-pulse rounded-lg bg-white" />
        ))}
      </div>
    );
  }
  if ("error" in current) {
    return (
      <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border border-[#dde3e9] bg-white py-10 text-center">
        <TriangleAlert className="size-9 text-amber-500" />
        <p className="max-w-sm text-gray-600">{current.error.detail}</p>
        <button
          type="button"
          onClick={retry}
          className="inline-flex items-center gap-2 rounded-md bg-brand-navy px-5 py-2.5 text-sm font-bold text-white"
        >
          <RefreshCw className="size-4" /> Reintentar
        </button>
      </div>
    );
  }
  return <>{children(current.data)}</>;
}

function Table({ head, rows }: { head: string[]; rows: { key: string; cells: ReactNode[] }[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-[#dde3e9] bg-white">
      <table className="w-full min-w-[52rem] text-left text-[13px]">
        <thead className="bg-[#f4f6f8] text-xs font-bold text-gray-500">
          <tr>
            {head.map((title) => (
              <th key={title} className="px-4 py-3">
                {title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="border-t border-[#dde3e9] align-top">
              {row.cells.map((cell, index) => (
                <td key={index} className="px-4 py-3 text-foreground">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Named({ name, code }: { name: string; code: string }) {
  return (
    <>
      <p className="font-medium text-foreground">{name}</p>
      {name !== code && <p className="text-xs text-gray-500">{code}</p>}
    </>
  );
}

function Badge({ tone, children }: { tone: "amber" | "muted" | "violet"; children: ReactNode }) {
  const styles = {
    amber: "bg-amber-500/15 text-amber-700",
    muted: "bg-slate-400/15 text-slate-600",
    violet: "bg-violet-600/10 text-violet-800",
  }[tone];
  return <span className={`whitespace-nowrap rounded px-2 py-1 text-[11px] font-bold ${styles}`}>{children}</span>;
}

function Stat({ label, value, tone = "navy" }: { label: string; value: number; tone?: "navy" | "amber" | "red" | "muted" }) {
  const color = { navy: "text-brand-navy", amber: "text-amber-600", red: "text-red-700", muted: "text-[#9aa7b4]" }[tone];
  return (
    <div className="rounded-lg border border-[#dde3e9] bg-white p-4">
      <p className="text-xs font-bold uppercase text-gray-500">{label}</p>
      <p className={`mt-1 text-3xl font-bold ${color}`}>{value}</p>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-[#dde3e9] bg-white py-12 text-center text-gray-500">
      <PackageCheck className="size-10 text-gray-400" />
      <p>{text}</p>
    </div>
  );
}

function Pager({ pagination, onPage }: { pagination: Pagination; onPage: (page: number) => void }) {
  const { page, pageSize, totalPages, total } = pagination;
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Páginas" className="flex items-center justify-end gap-3 text-sm text-gray-500">
      {(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} de {total}
      <button
        type="button"
        aria-label="Anterior"
        disabled={page <= 1}
        onClick={() => onPage(page - 1)}
        className="rounded-md border border-[#dde3e9] bg-white p-2 text-brand-navy disabled:text-gray-300"
      >
        <ChevronLeft className="size-4" />
      </button>
      <button
        type="button"
        aria-label="Siguiente"
        disabled={page >= totalPages}
        onClick={() => onPage(page + 1)}
        className="rounded-md border border-[#dde3e9] bg-white p-2 text-brand-navy disabled:text-gray-300"
      >
        <ChevronRight className="size-4" />
      </button>
    </nav>
  );
}
