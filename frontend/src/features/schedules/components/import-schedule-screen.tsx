"use client";

import {
  CircleCheck,
  Download,
  FileText,
  Loader2,
  TriangleAlert,
  UploadCloud,
  X,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState, type DragEvent, type ReactNode } from "react";
import { DatePicker } from "@/components/date-picker";
import { Dropdown } from "@/components/dropdown";
import { ApiRequestError } from "@/lib/api/client";
import { clockTime, dayOf, shortDay, timeOf } from "@/lib/dates";
import { confirmSchedule, validateSchedule } from "../api";
import { termOptions } from "../labels";
import type { ConfirmationResponse, ScheduleError, ValidationResponse } from "../types";
import { ReportTable } from "./report-table";

/** El tope de spring.servlet.multipart.max-file-size. */
const MAX_BYTES = 2 * 1024 * 1024;
const TEMPLATE_URL = "/plantillas/horarios.csv";

/**
 * Importar horarios semestrales (UC3 T033 y T034), según admin-importar-horarios: configurar y subir, revisar el
 * reporte por fila y confirmar el impacto. Validar no cambia nada; aplicar es todo o nada.
 */
export function ImportScheduleScreen() {
  const terms = termOptions();
  const [term, setTerm] = useState(terms.current);
  const [termStart, setTermStart] = useState("");
  const [termEnd, setTermEnd] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  const [validating, setValidating] = useState(false);
  const [report, setReport] = useState<ValidationResponse | null>(null);
  const [uploadError, setUploadError] = useState<ScheduleError | null>(null);

  const [applying, setApplying] = useState(false);
  const [applied, setApplied] = useState<ConfirmationResponse | null>(null);
  const [applyError, setApplyError] = useState<ScheduleError | null>(null);

  // Cualquier cambio en lo que se sube invalida el reporte que se tenía.
  function resetResult() {
    setReport(null);
    setUploadError(null);
    setApplyError(null);
    setApplied(null);
  }

  function chooseFile(candidate: File | undefined) {
    resetResult();
    setFileError(null);
    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith(".csv")) {
      setFile(null);
      setFileError("Solo se aceptan archivos .csv. Si lo tienes en Excel, guárdalo como «CSV UTF-8».");
      return;
    }
    if (candidate.size > MAX_BYTES) {
      setFile(null);
      setFileError("El archivo pasa de 2 MB.");
      return;
    }
    setFile(candidate);
  }

  const datesProblem =
    termStart && termEnd && termEnd <= termStart ? "El fin de clases debe ser posterior al inicio." : null;
  const canValidate = Boolean(term && termStart && termEnd && file && !datesProblem && !validating);

  async function validate() {
    if (!file) return;
    resetResult();
    setValidating(true);
    try {
      setReport(await validateSchedule({ file, academicTerm: term, termStart, termEnd }));
    } catch (error) {
      setUploadError(asScheduleError(error, "No se pudo validar el archivo."));
    } finally {
      setValidating(false);
    }
  }

  async function apply() {
    if (!report) return;
    setApplying(true);
    setApplyError(null);
    try {
      setApplied(await confirmSchedule(report.uploadId));
    } catch (error) {
      setApplyError(asScheduleError(error, "No se pudo aplicar la carga."));
    } finally {
      setApplying(false);
    }
  }

  function startOver() {
    resetResult();
    setFile(null);
  }

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <StepCard
        number={1}
        title="Configurar semestre y archivo"
        aside={
          <div className="w-56">
            <Dropdown
              label="Semestre"
              value={term}
              onChange={(value) => {
                setTerm(value);
                resetResult();
              }}
              options={terms.options.map((value) => ({ value, label: `Semestre: ${value}` }))}
            />
          </div>
        }
      >
        <div className="flex flex-col gap-2">
          <div className="grid gap-4 sm:grid-cols-2">
            <DateField
              label="Inicio de clases"
              value={termStart}
              onChange={(value) => {
                setTermStart(value);
                resetResult();
              }}
            />
            <DateField
              label="Fin de clases"
              value={termEnd}
              min={termStart || undefined}
              onChange={(value) => {
                setTermEnd(value);
                resetResult();
              }}
            />
          </div>
          <p className={`text-xs ${datesProblem ? "text-red-700" : "text-gray-500"}`}>
            {datesProblem ??
              "Cada clase semanal se repite entre estas fechas. Sábados, domingos y festivos no generan sesión."}
          </p>
        </div>

        <Dropzone file={file} onFile={chooseFile} onClear={() => chooseFile(undefined)} />
        {fileError && <Alert tone="error">{fileError}</Alert>}
        {uploadError && <UploadProblem error={uploadError} />}

        <div className="flex justify-end">
          <button
            type="button"
            onClick={validate}
            disabled={!canValidate}
            className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-brand-navy px-7 font-semibold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-gray-300"
          >
            {validating && <Loader2 className="size-4 animate-spin" />}
            {validating ? "Validando…" : "Validar archivo"}
          </button>
        </div>
      </StepCard>

      {report && (
        <>
          <StepCard number={2} title="Previsualizar resultados de carga">
            <div className="grid gap-5 sm:grid-cols-3">
              <Stat label="Clases a cargar" value={report.summary.rowsOk} tone="navy" />
              <Stat label="Ya cargadas (se omiten)" value={report.summary.rowsUnchanged} tone="muted" />
              <Stat
                label="Filas rechazadas"
                value={report.summary.rowsWithBlockingVerdict}
                tone={report.summary.rowsWithBlockingVerdict > 0 ? "red" : "muted"}
              />
            </div>
            <p className="text-sm text-gray-500">
              {report.summary.rowsRead} filas leídas, {report.summary.sessionsToCreate} sesiones en el periodo
              {report.summary.sessionsSkippedNonBusinessDay > 0 &&
                ` y ${report.summary.sessionsSkippedNonBusinessDay} omitidas por caer en día no hábil`}
            </p>
            <ReportTable rows={report.rows} />
          </StepCard>

          <StepCard number={3} title="Confirmación de impacto en reservas">
            {applied ? (
              <Applied result={applied} onStartOver={startOver} />
            ) : (
              <Impact
                report={report}
                applying={applying}
                applyError={applyError}
                onApply={apply}
                onRevalidate={validate}
                onCancel={startOver}
              />
            )}
          </StepCard>
        </>
      )}
    </div>
  );
}

function Impact({
  report,
  applying,
  applyError,
  onApply,
  onRevalidate,
  onCancel,
}: {
  report: ValidationResponse;
  applying: boolean;
  applyError: ScheduleError | null;
  onApply: () => void;
  onRevalidate: () => void;
  onCancel: () => void;
}) {
  const [listOpen, setListOpen] = useState(false);
  const now = useNow();
  const expired = report.expiresAt !== undefined && Date.parse(report.expiresAt) <= now;
  const { reservationsToCancel, blocksToWithdraw, rowsWithBlockingVerdict } = report.summary;
  const affected = report.rows.flatMap((row) =>
    (row.affectedReservations ?? []).map((reservation) => ({ row, reservation })),
  );
  const needsRevalidation = expired || applyError?.code === "UPLOAD_EXPIRED" || applyError?.code === "IMPACT_CHANGED";

  return (
    <>
      {!report.applicable ? (
        <Banner tone="error" title={`La carga no se puede aplicar: ${plural(rowsWithBlockingVerdict, "fila tiene", "filas tienen")} problemas.`}>
          Corrige esas filas en el archivo y vuelve a subirlo. No se canceló ninguna reserva ni se creó ningún bloqueo.
        </Banner>
      ) : report.requiresConfirmation ? (
        <Banner
          tone="warning"
          title={[
            reservationsToCancel > 0 &&
              `Esta carga cancelará ${plural(reservationsToCancel, "reserva estudiantil confirmada", "reservas estudiantiles confirmadas")}`,
            blocksToWithdraw > 0 && `retirará ${plural(blocksToWithdraw, "clase que ya no viene", "clases que ya no vienen")} en el archivo`,
          ]
            .filter(Boolean)
            .join(" y ")
            .concat(".")}
          action={
            affected.length > 0 && (
              <button
                type="button"
                onClick={() => setListOpen((open) => !open)}
                className="shrink-0 rounded-full border border-amber-600 px-4 py-1.5 text-sm font-semibold text-amber-700 transition hover:bg-amber-500/10"
              >
                {listOpen ? "Ocultar listado" : "Ver listado"}
              </button>
            )
          }
        >
          Quedan como «Cancelada por prioridad académica», no cuentan en contra de los estudiantes y se reportan al
          Módulo 3.
        </Banner>
      ) : (
        <Banner tone="ok" title="Esta carga no cancela ninguna reserva.">
          Solo se crean los bloqueos de las clases nuevas.
        </Banner>
      )}

      {listOpen && <AffectedList items={affected} />}

      {applyError && (
        <Alert tone="error">
          {applyError.detail}
          {applyError.code === "UPLOAD_ALREADY_APPLIED" && " No se aplicó dos veces."}
        </Alert>
      )}

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="max-w-md text-xs text-gray-500">
          La carga se aplica completa o no se aplica.
          {report.applicable &&
            report.expiresAt &&
            (expired
              ? " Esta validación ya venció: vuelve a validar el archivo."
              : ` Esta validación vence a las ${clockTime(report.expiresAt)}; después hay que volver a validar el archivo.`)}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <button
            type="button"
            onClick={onCancel}
            className="h-12 rounded-full border border-gray-200 bg-white px-7 font-semibold text-foreground transition hover:bg-gray-100"
          >
            Cancelar proceso
          </button>
          {report.applicable && needsRevalidation ? (
            <button
              type="button"
              onClick={onRevalidate}
              className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-brand-navy px-7 font-semibold text-white transition hover:bg-brand-navy/90"
            >
              Volver a validar
            </button>
          ) : (
            <button
              type="button"
              onClick={onApply}
              disabled={!report.applicable || applying}
              className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-brand-navy px-7 font-semibold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-gray-300"
            >
              {applying && <Loader2 className="size-4 animate-spin" />}
              {applying ? "Aplicando…" : "Aplicar carga completa"}
            </button>
          )}
        </div>
      </div>
    </>
  );
}

function AffectedList({ items }: { items: { row: ValidationResponse["rows"][number]; reservation: NonNullable<ValidationResponse["rows"][number]["affectedReservations"]>[number] }[] }) {
  return (
    <div className="max-h-80 overflow-auto rounded-2xl border border-gray-200">
      <table className="w-full min-w-[40rem] text-left text-sm">
        <thead className="sticky top-0 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
          <tr>
            <th className="px-3 py-3">Estudiante</th>
            <th className="px-3 py-3">Recurso</th>
            <th className="px-3 py-3">Reserva</th>
            <th className="px-3 py-3">La desplaza</th>
          </tr>
        </thead>
        <tbody>
          {items.map(({ row, reservation }) => (
            <tr key={reservation.reservationId} className="border-t border-gray-100">
              <td className="px-3 py-3">
                <p className="font-medium text-foreground">{reservation.holder.name}</p>
                <p className="text-xs text-gray-500">{reservation.holder.code}</p>
              </td>
              <td className="px-3 py-3 text-foreground">{row.resourceName ?? row.resourceId}</td>
              <td className="whitespace-nowrap px-3 py-3 text-foreground">
                {shortDay(dayOf(reservation.start))}, {timeOf(reservation.start)} - {timeOf(reservation.end)}
              </td>
              <td className="px-3 py-3 text-gray-600">
                Fila {row.row}: {row.course}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Applied({ result, onStartOver }: { result: ConfirmationResponse; onStartOver: () => void }) {
  const { blocksCreated, blocksUnchanged, blocksWithdrawn, reservationsCancelled } = result.result;
  return (
    <div className="flex flex-col items-start gap-4 rounded-2xl bg-green-50 p-5 sm:p-6">
      <p className="flex items-center gap-2 text-lg font-bold text-green-800">
        <CircleCheck className="size-6" /> Carga del semestre {result.academicTerm} aplicada
      </p>
      <ul className="grid gap-x-10 gap-y-1 text-sm text-foreground sm:grid-cols-2">
        <li>{plural(blocksCreated, "sesión de clase creada", "sesiones de clase creadas")}</li>
        <li>{plural(blocksUnchanged, "sesión ya existía", "sesiones ya existían")}</li>
        <li>{plural(blocksWithdrawn, "sesión retirada", "sesiones retiradas")}</li>
        <li>{plural(reservationsCancelled, "reserva cancelada", "reservas canceladas")}</li>
      </ul>
      <p className="text-xs text-gray-500">Aplicada a las {clockTime(result.appliedAt)}</p>
      <button
        type="button"
        onClick={onStartOver}
        className="h-11 rounded-full border-2 border-brand-navy px-6 font-semibold text-brand-navy transition hover:bg-brand-navy/5"
      >
        Cargar otro archivo
      </button>
    </div>
  );
}

function Dropzone({ file, onFile, onClear }: { file: File | null; onFile: (file?: File) => void; onClear: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);

  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setOver(false);
    onFile(event.dataTransfer.files[0]);
  }

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={drop}
      className={`flex flex-col items-center gap-3 rounded-3xl border-2 border-dashed px-6 py-12 text-center transition ${
        over ? "border-brand-navy bg-brand-navy/5" : "border-gray-200 bg-gray-50"
      }`}
    >
      <input
        ref={input}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={(event) => {
          onFile(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      {file ? (
        <div className="flex items-center gap-3 rounded-2xl border border-gray-200 bg-white px-4 py-3">
          <FileText className="size-6 text-brand-navy" />
          <div className="text-left">
            <p className="text-sm font-bold text-foreground">{file.name}</p>
            <p className="text-xs text-gray-500">{(file.size / 1024).toFixed(1)} KB</p>
          </div>
          <button
            type="button"
            aria-label="Quitar el archivo"
            onClick={onClear}
            className="ml-2 rounded-full p-1 text-gray-500 transition hover:bg-gray-100"
          >
            <X className="size-4" />
          </button>
        </div>
      ) : (
        <>
          <UploadCloud className="size-9 text-brand-navy" />
          <button type="button" onClick={() => input.current?.click()} className="text-base font-semibold text-foreground">
            Arrastra la plantilla de horarios aquí o haz clic para buscar
          </button>
          <p className="text-sm text-gray-500">Solo archivos .csv de hasta 2 MB, con las columnas de la plantilla</p>
        </>
      )}
      <div className="mt-1 flex flex-wrap items-center justify-center gap-4">
        <a
          href={TEMPLATE_URL}
          download="plantilla-horarios.csv"
          className="inline-flex h-11 items-center gap-2 rounded-full border-2 border-brand-navy bg-white px-5 text-sm font-semibold text-brand-navy transition hover:bg-brand-navy/5"
        >
          <Download className="size-4" /> Descargar plantilla vacía
        </a>
        <Link href="/schedules/format" className="text-sm font-semibold text-brand-navy underline underline-offset-4">
          ¿Cómo debe ser el archivo?
        </Link>
      </div>
    </div>
  );
}

function UploadProblem({ error }: { error: ScheduleError }) {
  return (
    <Alert tone="error">
      <p>{error.status === 413 ? "El archivo pasa de 2 MB." : error.detail}</p>
      {error.missingColumns && error.missingColumns.length > 0 && (
        <p className="mt-1">Faltan las columnas: {error.missingColumns.join(", ")}.</p>
      )}
      {(error.code === "INVALID_HEADER" || error.code === "EMPTY_FILE") && (
        <Link href="/schedules/format" className="mt-1 inline-block font-bold underline">
          Ver cómo debe ser el archivo
        </Link>
      )}
    </Alert>
  );
}

function StepCard({
  number,
  title,
  aside,
  children,
}: {
  number: number;
  title: string;
  aside?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-5 rounded-3xl border border-gray-200 bg-white p-5 sm:p-7">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="flex items-center gap-3 text-lg font-bold text-foreground">
          <span className="flex size-8 items-center justify-center rounded-full bg-brand-navy text-sm font-bold text-white">
            {number}
          </span>
          {title}
        </h2>
        {aside}
      </div>
      {children}
    </section>
  );
}

function DateField({
  label,
  value,
  min,
  onChange,
}: {
  label: string;
  value: string;
  min?: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-base font-medium text-foreground">{label}</span>
      <DatePicker label={label} value={value} min={min} onChange={onChange} />
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone: "navy" | "muted" | "red" }) {
  const color = tone === "navy" ? "text-brand-navy" : tone === "red" ? "text-red-700" : "text-gray-400";
  return (
    <div className="rounded-2xl bg-gray-50 p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</p>
      <p className={`mt-2 text-3xl font-bold ${color}`}>{value}</p>
    </div>
  );
}

function Banner({
  tone,
  title,
  action,
  children,
}: {
  tone: "warning" | "error" | "ok";
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  const styles = {
    warning: "border-amber-600 bg-amber-500/10 text-amber-600",
    error: "border-red-700 bg-red-600/5 text-red-700",
    ok: "border-green-700/40 bg-green-600/5 text-green-700",
  }[tone];
  const Icon = tone === "ok" ? CircleCheck : TriangleAlert;
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`flex items-start gap-4 rounded-2xl border p-4 sm:p-5 ${styles}`}>
      <Icon className="mt-0.5 size-6 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-base font-semibold text-foreground">{title}</p>
        <p className="mt-1 text-sm text-gray-600">{children}</p>
      </div>
      {action}
    </div>
  );
}

function Alert({ tone, children }: { tone: "error"; children: ReactNode }) {
  return (
    <div role="alert" className={`rounded-2xl px-4 py-3 text-sm ${tone === "error" ? "bg-red-50 text-red-800" : ""}`}>
      {children}
    </div>
  );
}

function asScheduleError(error: unknown, fallback: string): ScheduleError {
  return error instanceof ApiRequestError
    ? (error.problem as ScheduleError)
    : { type: "about:blank", title: "Error", status: 0, detail: fallback };
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

/** La hora actual, refrescada cada 30 s, para que el vencimiento se note sin recargar. */
function useNow(): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}
