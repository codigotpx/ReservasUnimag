"use client";

import {
  Calendar,
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
import { Dropdown } from "@/components/dropdown";
import { ApiRequestError } from "@/lib/api/client";
import { clockTime } from "@/lib/dates";
import { confirmSchedule, validateSchedule } from "../api";
import { termOptions } from "../labels";
import type { ConfirmationResponse, ScheduleError, ValidationResponse } from "../types";
import { AffectedList } from "./affected-list";
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
            className="inline-flex items-center gap-2 rounded-md bg-brand-navy px-6 py-3 text-sm font-bold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-[#c5cdd5]"
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
              {report.summary.rowsRead} filas leídas · {report.summary.sessionsToCreate} sesiones en el periodo
              {report.summary.sessionsSkippedNonBusinessDay > 0 &&
                ` · ${report.summary.sessionsSkippedNonBusinessDay} omitidas por caer en día no hábil`}
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
            blocksToWithdraw > 0 &&
              `retirará ${plural(blocksToWithdraw, "sesión de una clase que ya no viene", "sesiones de clases que ya no vienen")} en el archivo`,
          ]
            .filter(Boolean)
            .join(" y ")
            .concat(".")}
          action={
            affected.length > 0 && (
              <button
                type="button"
                onClick={() => setListOpen((open) => !open)}
                className="shrink-0 rounded border border-amber-600 px-3 py-1 text-xs font-bold text-amber-700 transition hover:bg-amber-500/10"
              >
                {listOpen ? "Ocultar listado" : "Ver listado"}
              </button>
            )
          }
        >
          {reservationsToCancel > 0 &&
            "Las reservas quedan como «Cancelada por prioridad académica», no cuentan en contra de los estudiantes y se reportan al Módulo 3. "}
          {blocksToWithdraw > 0 &&
            "Una carga del mismo semestre reemplaza a la anterior: lo que no venga en este archivo deja de estar bloqueado."}
        </Banner>
      ) : (
        <Banner tone="ok" title="Esta carga no cancela ninguna reserva.">
          Solo se crean los bloqueos de las clases nuevas.
        </Banner>
      )}

      {listOpen && <AffectedList rows={report.rows} />}

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
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md border border-[#dde3e9] bg-white px-6 py-3 text-sm font-bold text-foreground transition hover:bg-gray-50"
          >
            Cancelar proceso
          </button>
          {report.applicable && needsRevalidation ? (
            <button
              type="button"
              onClick={onRevalidate}
              className="rounded-md bg-brand-navy px-6 py-3 text-sm font-bold text-white transition hover:bg-brand-navy/90"
            >
              Volver a validar
            </button>
          ) : (
            <button
              type="button"
              onClick={onApply}
              disabled={!report.applicable || applying}
              className="inline-flex items-center gap-2 rounded-md bg-brand-navy px-6 py-3 text-sm font-bold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-[#c5cdd5]"
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

function Applied({ result, onStartOver }: { result: ConfirmationResponse; onStartOver: () => void }) {
  const { blocksCreated, blocksUnchanged, blocksWithdrawn, reservationsCancelled } = result.result;
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg border border-green-600/30 bg-green-600/5 p-5">
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
        className="rounded-md border border-brand-navy px-5 py-2.5 text-sm font-bold text-brand-navy transition hover:bg-brand-navy/5"
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
      className={`flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-10 text-center transition ${
        over ? "border-brand-navy bg-brand-navy/5" : "border-[#c5cdd5] bg-[#f4f6f8]"
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
        <div className="flex items-center gap-3 rounded-lg border border-[#dde3e9] bg-white px-4 py-3">
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
          <button type="button" onClick={() => input.current?.click()} className="text-[15px] font-bold text-foreground">
            Arrastra la plantilla de horarios aquí o haz clic para buscar
          </button>
          <p className="text-[13px] text-gray-500">Solo archivos .csv de hasta 2 MB, con las columnas de la plantilla</p>
        </>
      )}
      <div className="mt-1 flex flex-wrap items-center justify-center gap-4">
        <a
          href={TEMPLATE_URL}
          download="plantilla-horarios.csv"
          className="inline-flex items-center gap-2 rounded-md border border-brand-navy bg-white px-4 py-2 text-[13px] font-bold text-brand-navy transition hover:bg-brand-navy/5"
        >
          <Download className="size-4" /> Descargar plantilla vacía
        </a>
        <Link href="/schedules/format" className="text-[13px] font-bold text-brand-navy underline">
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
    <section className="flex flex-col gap-5 rounded-lg border border-[#dde3e9] bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="flex items-center gap-3 text-lg font-bold text-foreground">
          <span className="flex size-7 items-center justify-center rounded-full bg-brand-navy text-sm text-white">
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
    <label className="flex flex-col gap-1.5">
      <span className="text-[13px] font-bold text-foreground">{label}</span>
      <span className="relative flex items-center rounded-md border border-[#dde3e9] focus-within:border-brand-navy focus-within:ring-2 focus-within:ring-brand-navy/20">
        <Calendar className="pointer-events-none absolute left-3.5 size-4 text-gray-500" />
        <input
          type="date"
          required
          value={value}
          min={min}
          onChange={(event) => onChange(event.target.value)}
          onClick={(event) => event.currentTarget.showPicker?.()}
          className="w-full cursor-pointer bg-transparent py-2.5 pl-10 pr-3 text-sm text-foreground outline-none [&::-webkit-calendar-picker-indicator]:hidden"
        />
      </span>
    </label>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone: "navy" | "muted" | "red" }) {
  const color = tone === "navy" ? "text-brand-navy" : tone === "red" ? "text-red-700" : "text-[#9aa7b4]";
  return (
    <div className="rounded-lg bg-[#f4f6f8] p-4">
      <p className="text-xs font-bold uppercase text-gray-500">{label}</p>
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
    <div role={tone === "error" ? "alert" : "status"} className={`flex items-start gap-4 rounded-lg border p-4 ${styles}`}>
      <Icon className="mt-0.5 size-6 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-bold text-foreground">{title}</p>
        <p className="mt-1 text-[13px] text-gray-600">{children}</p>
      </div>
      {action}
    </div>
  );
}

function Alert({ tone, children }: { tone: "error"; children: ReactNode }) {
  return (
    <div role="alert" className={`rounded-lg px-4 py-3 text-sm ${tone === "error" ? "bg-red-50 text-red-800" : ""}`}>
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
