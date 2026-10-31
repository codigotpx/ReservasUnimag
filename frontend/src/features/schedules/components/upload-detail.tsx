"use client";

import { ArrowLeft, Loader2, RefreshCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { clockTime, dateTimeLabel, dayMonth } from "@/lib/dates";
import { confirmSchedule, fetchUpload } from "../api";
import { uploadStatus } from "../labels";
import type { ScheduleError, ScheduleUploadDetail } from "../types";
import { AffectedList } from "./affected-list";
import { ReportTable } from "./report-table";

type Outcome = { key: number; data: ScheduleUploadDetail } | { key: number; error: ApiError };

/**
 * Una carga del histórico con su reporte por fila (GET /api/schedules/{id}). Si quedó validada y todavía está en plazo,
 * se puede aplicar desde aquí, igual que al terminar de validarla.
 */
export function UploadDetail({ uploadId }: { uploadId: string }) {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<ScheduleError | null>(null);
  const [now] = useState(() => Date.now());

  useEffect(() => {
    let active = true;
    fetchUpload(uploadId)
      .then((data) => active && setOutcome({ key: attempt, data }))
      .catch((error) => {
        if (!active) return;
        const problem: ApiError =
          error instanceof ApiRequestError
            ? error.problem
            : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo cargar la carga." };
        if (problem.status === 401) {
          router.replace("/login");
          return;
        }
        setOutcome({ key: attempt, error: problem });
      });
    return () => {
      active = false;
    };
  }, [uploadId, attempt, router]);

  const current = outcome?.key === attempt ? outcome : null;

  async function apply() {
    setApplying(true);
    setApplyError(null);
    try {
      await confirmSchedule(uploadId);
      setAttempt((value) => value + 1);
    } catch (error) {
      setApplyError(
        error instanceof ApiRequestError
          ? (error.problem as ScheduleError)
          : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo aplicar la carga." },
      );
    } finally {
      setApplying(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <Link
        href="/schedules/history"
        className="inline-flex w-fit items-center gap-2 text-sm font-bold text-brand-navy hover:underline"
      >
        <ArrowLeft className="size-4" /> Horarios importados
      </Link>

      {!current ? (
        <div aria-busy="true" className="h-64 animate-pulse rounded-3xl border border-gray-200 bg-white" />
      ) : "error" in current ? (
        <Card>
          <div role="alert" className="flex flex-col items-center gap-3 py-8 text-center">
            <TriangleAlert className="size-9 text-amber-500" />
            <p className="max-w-sm text-gray-600">
              {current.error.status === 404 ? "Esa carga no existe." : current.error.detail}
            </p>
            {current.error.status !== 404 && (
              <button
                type="button"
                onClick={() => setAttempt((value) => value + 1)}
                className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-5 py-2.5 text-sm font-semibold text-white"
              >
                <RefreshCw className="size-4" /> Reintentar
              </button>
            )}
          </div>
        </Card>
      ) : (
        <Detail upload={current.data} now={now} applying={applying} applyError={applyError} onApply={apply} />
      )}
    </div>
  );
}

function Detail({
  upload,
  now,
  applying,
  applyError,
  onApply,
}: {
  upload: ScheduleUploadDetail;
  now: number;
  applying: boolean;
  applyError: ScheduleError | null;
  onApply: () => void;
}) {
  const status = uploadStatus(upload.status, upload.expiresAt, now);
  const pending =
    upload.status === "VALIDATED" && upload.applicable && upload.expiresAt !== undefined && Date.parse(upload.expiresAt) > now;
  const affected = upload.rows.some((row) => (row.affectedReservations ?? []).length > 0);

  return (
    <>
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-foreground">Semestre {upload.academicTerm}</h2>
          <span className={`rounded px-2.5 py-1 text-xs font-bold ${status.className}`}>{status.label}</span>
        </div>
        <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <Info label="Archivo">{upload.fileName ?? "Sin nombre"}</Info>
          <Info label="Periodo de clases">
            {dayMonth(upload.term.start)} al {dayMonth(upload.term.end)}
          </Info>
          <Info label="Subida por">{upload.uploadedBy.name ?? "Sin nombre"}</Info>
          <Info label="Validada">{dateTimeLabel(upload.validatedAt)}</Info>
          <Info label="Aplicada">{upload.appliedAt ? dateTimeLabel(upload.appliedAt) : "Sin aplicar"}</Info>
        </dl>

        {upload.result && (
          <div className="grid gap-4 sm:grid-cols-4">
            <Stat label="Sesiones creadas" value={upload.result.blocksCreated} />
            <Stat label="Ya existían" value={upload.result.blocksUnchanged} />
            <Stat label="Sesiones retiradas" value={upload.result.blocksWithdrawn} />
            <Stat label="Reservas canceladas" value={upload.result.reservationsCancelled} />
          </div>
        )}

        {upload.status === "REJECTED" && (
          <Note tone="error">
            No se aplicó: {upload.summary.rowsWithBlockingVerdict}{" "}
            {upload.summary.rowsWithBlockingVerdict === 1 ? "fila tenía" : "filas tenían"} problemas. No se canceló ninguna
            reserva ni se creó ningún bloqueo.
          </Note>
        )}
        {upload.status !== "APPLIED" && upload.status !== "REJECTED" && !pending && (
          <Note tone="muted">
            Venció sin aplicarse. Para aplicarla hay que{" "}
            <Link href="/schedules" className="font-bold text-brand-navy underline">
              volver a subir el archivo
            </Link>
            .
          </Note>
        )}
        {pending && (
          <div className="flex flex-col gap-3 rounded-2xl border border-amber-600 bg-amber-500/10 p-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-foreground">
              Está validada pero no se aplicó; vence a las <strong>{clockTime(upload.expiresAt!)}</strong>
              {impactOf(upload.summary) ? ` Al aplicarla ${impactOf(upload.summary)}.` : ""}
            </p>
            <button
              type="button"
              onClick={onApply}
              disabled={applying}
              className="inline-flex shrink-0 items-center justify-center gap-2 rounded-full bg-brand-navy px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
            >
              {applying && <Loader2 className="size-4 animate-spin" />}
              {applying ? "Aplicando…" : "Aplicar carga completa"}
            </button>
          </div>
        )}
        {applyError && <Note tone="error">{applyError.detail}</Note>}
      </Card>

      <Card>
        <h2 className="text-lg font-bold text-foreground">Reporte por fila</h2>
        <p className="text-sm text-gray-500">
          {upload.summary.rowsRead} filas leídas: {upload.summary.rowsOk} a cargar, {upload.summary.rowsUnchanged} ya
          cargadas y {upload.summary.rowsWithBlockingVerdict} rechazadas.
        </p>
        <ReportTable rows={upload.rows} />
      </Card>

      {affected && (
        <Card>
          <h2 className="text-lg font-bold text-foreground">
            {upload.status === "APPLIED" ? "Reservas que canceló" : "Reservas que cancelaría"}
          </h2>
          <AffectedList rows={upload.rows} />
        </Card>
      )}
    </>
  );
}

/**
 * Lo que cambia al aplicar, además de crear los bloqueos: reservas canceladas y clases retiradas. Una carga nueva del
 * mismo semestre reemplaza a la anterior, así que las clases que ya no vienen en el archivo se retiran.
 */
function impactOf(summary: ScheduleUploadDetail["summary"]): string {
  const parts = [
    summary.reservationsToCancel > 0 &&
      `cancelará ${summary.reservationsToCancel} ${summary.reservationsToCancel === 1 ? "reserva" : "reservas"}`,
    summary.blocksToWithdraw > 0 &&
      `retirará ${summary.blocksToWithdraw} ${summary.blocksToWithdraw === 1 ? "sesión" : "sesiones"} de clases que ya no vienen en el archivo`,
  ].filter(Boolean);
  return parts.join(" y ");
}

function Card({ children }: { children: ReactNode }) {
  return <section className="flex flex-col gap-5 rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">{children}</section>;
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-bold uppercase text-gray-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-foreground">{children}</dd>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl bg-gray-50 p-5">
      <p className="text-xs font-bold uppercase text-gray-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-brand-navy">{value}</p>
    </div>
  );
}

function Note({ tone, children }: { tone: "error" | "muted"; children: ReactNode }) {
  return (
    <p
      role={tone === "error" ? "alert" : undefined}
      className={`rounded-2xl px-4 py-3 text-sm ${tone === "error" ? "bg-red-50 text-red-800" : "bg-gray-50 text-gray-600"}`}
    >
      {children}
    </p>
  );
}
