"use client";

import { CircleCheck, Loader2, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent, type ReactNode } from "react";
import { DatePicker } from "@/components/date-picker";
import { Dropdown } from "@/components/dropdown";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { HALF_HOURS, dayOf, fullDayLabel, timeOf, today } from "@/lib/dates";
import { registerExtraordinaryNeed } from "../api";
import { VERDICTS } from "../labels";
import type { ExtraordinaryNeedRequest, ExtraordinaryNeedResponse, Verdict } from "../types";
import { AffectedList } from "./affected-list";

type Problem = ApiError & { verdict?: Verdict; conflictingBlock?: { course: string; courseCode: string; group: string } };

const EMPTY = {
  resourceId: "",
  date: "",
  start: "08:00",
  end: "10:00",
  course: "",
  courseCode: "",
  group: "1",
  program: "",
  instructor: "",
};

/**
 * Actividades extraordinarias (UC3 T038): bloquear un recurso para una sola sesión. Primero se revisa el impacto sin
 * cambiar nada (confirm: false) y después se confirma; el tope de 2 horas no aplica, por ser académica.
 */
export function ExtraordinaryNeedForm() {
  const [form, setForm] = useState(EMPTY);
  const [checking, setChecking] = useState(false);
  const [preview, setPreview] = useState<ExtraordinaryNeedResponse | null>(null);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [done, setDone] = useState<ExtraordinaryNeedResponse | null>(null);

  // Cualquier cambio invalida la revisión: lo que se confirma es exactamente lo que se revisó.
  function update<K extends keyof typeof EMPTY>(key: K, value: (typeof EMPTY)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setPreview(null);
    setProblem(null);
  }

  const slotProblem = form.end <= form.start ? "La hora de fin debe ser posterior a la de inicio." : null;
  const complete =
    form.resourceId.trim() && form.date && form.course.trim() && form.courseCode.trim() && form.group.trim() && form.program.trim();

  function request(confirm: boolean): ExtraordinaryNeedRequest {
    return {
      resourceId: form.resourceId.trim().toUpperCase(),
      date: form.date,
      start: form.start,
      end: form.end,
      course: form.course.trim(),
      courseCode: form.courseCode.trim(),
      group: form.group.trim(),
      program: form.program.trim(),
      instructor: form.instructor.trim() || undefined,
      confirm,
    };
  }

  async function check(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setChecking(true);
    setProblem(null);
    setPreview(null);
    try {
      setPreview(await registerExtraordinaryNeed(request(false)));
    } catch (error) {
      setProblem(asProblem(error));
    } finally {
      setChecking(false);
    }
  }

  async function confirm() {
    setConfirming(true);
    setProblem(null);
    try {
      setDone(await registerExtraordinaryNeed(request(true)));
    } catch (error) {
      setProblem(asProblem(error));
    } finally {
      setConfirming(false);
    }
  }

  function startOver() {
    setForm(EMPTY);
    setPreview(null);
    setProblem(null);
    setDone(null);
  }

  if (done?.block) {
    return (
      <Shell>
        <Card title="Bloqueo creado">
          <div className="flex flex-col items-start gap-4 rounded-2xl border border-green-600/30 bg-green-600/5 p-5">
            <p className="flex items-center gap-2 text-lg font-bold text-green-800">
              <CircleCheck className="size-6" /> {done.block.course}
            </p>
            <p className="text-sm text-foreground">
              {done.block.resourceId}, {fullDayLabel(dayOf(done.block.start))}, de {timeOf(done.block.start)} a{" "}
              {timeOf(done.block.end)}
            </p>
            {(done.reservationsCancelled ?? 0) > 0 && (
              <p className="text-sm text-foreground">
                {done.reservationsCancelled === 1
                  ? "Se canceló 1 reserva estudiantil, que queda"
                  : `Se cancelaron ${done.reservationsCancelled} reservas estudiantiles, que quedan`}{" "}
                como «Cancelada por prioridad académica».
              </p>
            )}
            <button
              type="button"
              onClick={startOver}
              className="rounded-full border border-brand-navy px-5 py-2.5 text-sm font-bold text-brand-navy transition hover:bg-brand-navy/5"
            >
              Registrar otra actividad
            </button>
          </div>
        </Card>
      </Shell>
    );
  }

  return (
    <Shell>
      <Card title="Actividad extraordinaria">
        <p className="text-sm text-gray-500">
          Bloquea un espacio o activo para una sola sesión: una visita de pares, un examen, un evento del programa. Primero
          revisa el impacto; nada cambia hasta que confirmes.
        </p>
        <form onSubmit={check} className="flex flex-col gap-5">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Recurso" hint={<>El código, como <code>ESP-0412</code>. Lo ves en Consultar recursos.</>}>
              <input
                value={form.resourceId}
                onChange={(event) => update("resourceId", event.target.value)}
                placeholder="ESP-0412"
                required
                className={inputClass}
              />
            </Field>
            <Field label="Fecha">
              <DatePicker label="Fecha" value={form.date} min={today()} onChange={(value) => update("date", value)} />
            </Field>
            <Field label="Hora inicio">
              <Dropdown
                label="Hora inicio"
                value={form.start}
                onChange={(value) => update("start", value)}
                options={HALF_HOURS.slice(0, -1).map((time) => ({ value: time, label: time }))}
               
              />
            </Field>
            <Field label="Hora fin" hint={slotProblem ?? "Sin tope de 2 horas: es una actividad académica."} error={Boolean(slotProblem)}>
              <Dropdown
                label="Hora fin"
                value={form.end}
                onChange={(value) => update("end", value)}
                options={HALF_HOURS.slice(1).map((time) => ({ value: time, label: time }))}
               
              />
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Actividad">
              <input
                value={form.course}
                onChange={(event) => update("course", event.target.value)}
                placeholder="Visita de pares académicos"
                required
                className={inputClass}
              />
            </Field>
            <Field label="Código">
              <input
                value={form.courseCode}
                onChange={(event) => update("courseCode", event.target.value)}
                placeholder="EXT-001"
                required
                className={inputClass}
              />
            </Field>
            <Field label="Grupo">
              <input
                value={form.group}
                onChange={(event) => update("group", event.target.value)}
                required
                className={inputClass}
              />
            </Field>
            <Field label="Programa">
              <input
                value={form.program}
                onChange={(event) => update("program", event.target.value)}
                placeholder="Ingeniería de Sistemas"
                required
                className={inputClass}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Responsable (opcional)">
              <input
                value={form.instructor}
                onChange={(event) => update("instructor", event.target.value)}
                placeholder="Nombre del docente o responsable"
                className={inputClass}
              />
            </Field>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={!complete || Boolean(slotProblem) || checking}
              className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand-navy/90 disabled:cursor-not-allowed disabled:bg-[#c5cdd5]"
            >
              {checking && <Loader2 className="size-4 animate-spin" />}
              {checking ? "Revisando…" : "Revisar impacto"}
            </button>
          </div>
        </form>
      </Card>

      {problem && (
        <Card title="No se puede bloquear esa franja">
          <div role="alert" className="flex gap-3 rounded-2xl border border-red-700 bg-red-600/5 p-4">
            <TriangleAlert className="mt-0.5 size-6 shrink-0 text-red-700" />
            <div className="text-sm">
              {problem.verdict && <p className="font-bold text-red-700">{VERDICTS[problem.verdict]?.label}</p>}
              <p className="mt-1 text-gray-700">
                {problem.status === 404 ? "Ese recurso no existe en el inventario. Revisa el código." : problem.detail}
              </p>
            </div>
          </div>
        </Card>
      )}

      {preview && (
        <Card title="Impacto">
          <p className="text-sm text-foreground">
            <strong>{preview.resourceName ?? preview.resourceId}</strong>
            {preview.start && preview.end && (
              <>
                {" "}
                , {fullDayLabel(dayOf(preview.start))}, de {timeOf(preview.start)} a {timeOf(preview.end)}
              </>
            )}
          </p>
          {preview.verdict === "DISPLACES_RESERVATIONS" && preview.affectedReservations?.length ? (
            <>
              <div role="status" className="flex gap-3 rounded-2xl border border-amber-600 bg-amber-500/10 p-4">
                <TriangleAlert className="mt-0.5 size-6 shrink-0 text-amber-600" />
                <div className="text-sm">
                  <p className="font-bold text-foreground">
                    Este bloqueo cancelará {preview.affectedReservations.length}{" "}
                    {preview.affectedReservations.length === 1 ? "reserva estudiantil" : "reservas estudiantiles"}.
                  </p>
                  <p className="mt-1 text-gray-600">
                    Quedan como «Cancelada por prioridad académica», no cuentan en contra de los estudiantes y se reportan
                    al Módulo 3.
                  </p>
                </div>
              </div>
              <AffectedList reservations={preview.affectedReservations} resourceName={preview.resourceName ?? preview.resourceId} />
            </>
          ) : (
            <p role="status" className="flex items-center gap-2 rounded-2xl border border-green-700/40 bg-green-600/5 p-4 text-sm font-bold text-foreground">
              <CircleCheck className="size-5 text-green-700" /> La franja está libre: no se cancela ninguna reserva.
            </p>
          )}
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={() => setPreview(null)}
              className="rounded-full border border-gray-200 bg-white px-6 py-3 text-sm font-bold text-foreground transition hover:bg-gray-50"
            >
              Cambiar datos
            </button>
            <button
              type="button"
              onClick={confirm}
              disabled={confirming}
              className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand-navy/90 disabled:opacity-60"
            >
              {confirming && <Loader2 className="size-4 animate-spin" />}
              {confirming ? "Bloqueando…" : "Confirmar bloqueo"}
            </button>
          </div>
        </Card>
      )}
      <p className="text-xs text-gray-500">
        Para el horario de todo el semestre usa la{" "}
        <Link href="/schedules" className="font-bold text-brand-navy underline">
          carga académica
        </Link>
        .
      </p>
    </Shell>
  );
}

const inputClass =
  "w-full rounded-full border border-gray-200 bg-white px-3.5 py-2.5 text-sm text-foreground outline-none transition placeholder:text-gray-400 focus:border-brand-navy focus:ring-2 focus:ring-brand-navy/20";

function Shell({ children }: { children: ReactNode }) {
  return <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">{children}</div>;
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-5 rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">
      <h2 className="text-lg font-bold text-foreground">{title}</h2>
      {children}
    </section>
  );
}

function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: ReactNode;
  error?: boolean;
  children: ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-[13px] font-bold text-foreground">{label}</span>
      {children}
      {hint && <span className={`text-xs ${error ? "text-red-700" : "text-gray-500"}`}>{hint}</span>}
    </label>
  );
}

function asProblem(error: unknown): Problem {
  return error instanceof ApiRequestError
    ? (error.problem as Problem)
    : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo completar la solicitud." };
}
