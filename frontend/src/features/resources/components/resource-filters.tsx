"use client";

import { Clock, Layers, Minus, Plus } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { DatePicker } from "@/components/date-picker";
import { Dropdown } from "@/components/dropdown";
import { HALF_HOURS, addMinutes, longDayLabel, today } from "@/lib/dates";
import { CategoryToggle } from "./category-toggle";
import { TYPES_BY_CATEGORY, codeLabel } from "../labels";
import type { Category, SearchResourcesParams } from "../types";

const OPENING = "06:00";
const CLOSING = "22:00";
/** La duración que se propone al elegir la hora de inicio, la misma de la franja por defecto. */
const DEFAULT_MINUTES = 120;

/**
 * Fecha, franja, tipo y aforo mínimo (UC1 T039). Valida por usabilidad la ventana de 06:00 a 22:00;
 * la decisión final es del backend, cuyo 400 llega en serverError.
 */
export function ResourceFilters({
  initial,
  serverError,
  onSubmit,
}: {
  initial: SearchResourcesParams & { category: Category };
  serverError?: string;
  onSubmit: (search: SearchResourcesParams & { category: Category }) => void;
}) {
  const [category, setCategory] = useState(initial.category);
  const [date, setDate] = useState(initial.date);
  const [start, setStart] = useState(initial.start);
  const [end, setEnd] = useState(initial.end);
  const [type, setType] = useState(initial.type ?? "");
  const [minCapacity, setMinCapacity] = useState(initial.minCapacity ?? 1);
  const [error, setError] = useState<string | undefined>(serverError);

  function changeCategory(next: Category) {
    setCategory(next);
    setType("");
  }

  // Al mover el inicio, el fin se va dos horas después (sin pasar de las 22:00); si quiere otra duración, la cambia.
  function changeStart(next: string) {
    setStart(next);
    setEnd(addMinutes(next, DEFAULT_MINUTES));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = slotProblem(start, end);
    if (problem) {
      setError(problem);
      return;
    }
    setError(undefined);
    onSubmit({
      category,
      date,
      start,
      end,
      type: type || undefined,
      // Con 1 no se filtra: cualquier espacio sirve para una persona.
      minCapacity: category === "ESPACIO" && minCapacity > 1 ? minCapacity : undefined,
      page: 1,
    });
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <CategoryToggle value={category} onChange={changeCategory} />

      <div className="flex flex-col gap-5 rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">
        <Field label="Fecha">
          <DatePicker label="Fecha" value={date} min={today()} onChange={setDate} format={longDayLabel} />
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Hora inicio">
            <TimeInput label="Hora inicio" value={start} options={HALF_HOURS.slice(0, -1)} onChange={changeStart} />
          </Field>
          <Field label="Hora fin">
            <TimeInput label="Hora fin" value={end} options={HALF_HOURS.slice(1)} onChange={setEnd} />
          </Field>
        </div>

        <Field label={category === "ESPACIO" ? "Tipo de espacio" : "Tipo de activo"}>
          <Dropdown
            label={category === "ESPACIO" ? "Tipo de espacio" : "Tipo de activo"}
            icon={<Layers className="size-5" />}
            value={type}
            onChange={setType}
            options={[
              { value: "", label: "Todos" },
              ...TYPES_BY_CATEGORY[category].map((code) => ({ value: code, label: codeLabel(code) })),
            ]}
          />
        </Field>

        {category === "ESPACIO" && (
          <Field label="Capacidad mínima">
            <div className="flex h-12 items-center rounded-full border border-gray-200 px-2">
              <button
                type="button"
                aria-label="Menos"
                onClick={() => setMinCapacity((value) => Math.max(1, value - 1))}
                className="rounded-full p-2 text-brand-navy transition hover:bg-gray-100 disabled:text-gray-300"
                disabled={minCapacity <= 1}
              >
                <Minus className="size-5" />
              </button>
              <input
                type="number"
                aria-label="Capacidad mínima"
                inputMode="numeric"
                min={1}
                value={minCapacity}
                onChange={(event) => setMinCapacity(Math.max(1, Number(event.target.value) || 1))}
                className="w-full bg-transparent text-center text-lg font-semibold outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
              />
              <button
                type="button"
                aria-label="Más"
                onClick={() => setMinCapacity((value) => value + 1)}
                className="rounded-full p-2 text-brand-navy transition hover:bg-gray-100"
              >
                <Plus className="size-5" />
              </button>
            </div>
          </Field>
        )}

        {error && (
          <p role="alert" className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </p>
        )}
      </div>

      <button
        type="submit"
        className="h-13 rounded-full bg-brand-navy text-lg font-semibold text-white transition hover:bg-brand-navy/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-navy"
      >
        Buscar disponibilidad
      </button>
    </form>
  );
}

function slotProblem(start: string, end: string): string | undefined {
  if (start < OPENING || end > CLOSING) {
    return "La franja debe estar entre las 06:00 y las 22:00.";
  }
  if (end <= start) {
    return "La hora de fin debe ser posterior a la de inicio.";
  }
  return undefined;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-base font-medium text-foreground">{label}</span>
      {children}
    </div>
  );
}


function TimeInput({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  // Una hora que llegó por la URL y no cae en media hora se conserva como opción.
  const choices = options.includes(value) ? options : [value, ...options].sort();
  return (
    <Dropdown
      label={label}
      icon={<Clock className="size-5" />}
      value={value}
      onChange={onChange}
      options={choices.map((time) => ({ value: time, label: time }))}
    />
  );
}
