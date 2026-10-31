"use client";

import { Calendar, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { addDays, monthYear, today, weekdayDayMonth } from "@/lib/dates";

const WEEKDAYS: [short: string, long: string][] = [
  ["L", "lunes"],
  ["M", "martes"],
  ["M", "miércoles"],
  ["J", "jueves"],
  ["V", "viernes"],
  ["S", "sábado"],
  ["D", "domingo"],
];

/**
 * Selector de fecha propio, para no depender del calendario nativo, que se ve distinto en cada navegador y no sigue
 * el diseño. Trabaja con fechas yyyy-MM-dd. Se maneja con teclado como una grilla: flechas, Inicio y Fin de la semana,
 * Re Pág y Av Pág para cambiar de mes, Enter para elegir y Escape para cerrar.
 */
export function DatePicker({
  label,
  value,
  onChange,
  min,
  placeholder = "Elige una fecha",
  format = (date) => `${weekdayDayMonth(date)} de ${date.slice(0, 4)}`,
  className = "",
}: {
  /** Nombre accesible; la etiqueta visible la pone quien lo usa. */
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** La primera fecha que se puede elegir, yyyy-MM-dd. */
  min?: string;
  placeholder?: string;
  /** Cómo se muestra la fecha elegida en el botón. */
  format?: (date: string) => string;
  className?: string;
}) {
  const id = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [focused, setFocused] = useState(() => value || today());
  const current = today();
  const allowed = (date: string) => !min || date >= min;

  useEffect(() => {
    if (!open) return;
    function closeOnOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", closeOnOutside);
    return () => document.removeEventListener("pointerdown", closeOnOutside);
  }, [open]);

  useEffect(() => {
    if (open) gridRef.current?.querySelector<HTMLButtonElement>(`[data-date="${focused}"]`)?.focus();
  }, [open, focused]);

  function show() {
    const start = value || (min && min > current ? min : current);
    setFocused(start);
    setOpen(true);
  }

  function close() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  function choose(date: string) {
    if (!allowed(date)) return;
    onChange(date);
    close();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const weekday = (dayOfWeek(focused) + 6) % 7;
    const moves: Record<string, () => string> = {
      ArrowLeft: () => addDays(focused, -1),
      ArrowRight: () => addDays(focused, 1),
      ArrowUp: () => addDays(focused, -7),
      ArrowDown: () => addDays(focused, 7),
      Home: () => addDays(focused, -weekday),
      End: () => addDays(focused, 6 - weekday),
      PageUp: () => addMonths(focused, -1),
      PageDown: () => addMonths(focused, 1),
    };
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      return;
    }
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    setFocused(move());
  }

  const month = focused.slice(0, 7);
  const first = `${month}-01`;
  const lead = (dayOfWeek(first) + 6) % 7;
  const cells = Array.from({ length: Math.ceil((lead + daysInMonth(month)) / 7) * 7 }, (_, index) =>
    addDays(first, index - lead),
  );

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        aria-label={value ? `${label}: ${format(value)}` : label}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={`${id}-dialog`}
        onClick={() => (open ? setOpen(false) : show())}
        className={`flex h-12 w-full items-center gap-3 rounded-full border bg-white px-5 text-left text-base outline-none transition ${
          open ? "border-brand-navy ring-2 ring-brand-navy/20" : "border-gray-200 hover:border-gray-300"
        } focus-visible:border-brand-navy focus-visible:ring-2 focus-visible:ring-brand-navy/20`}
      >
        <Calendar className="size-5 shrink-0 text-gray-500" />
        <span className={`min-w-0 flex-1 truncate ${value ? "text-foreground" : "text-gray-400"}`}>
          {value ? format(value) : placeholder}
        </span>
      </button>

      {open && (
        <div
          id={`${id}-dialog`}
          role="dialog"
          aria-label={label}
          onKeyDown={handleKeyDown}
          className="absolute left-0 top-full z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-2xl border border-gray-200 bg-white p-4 shadow-xl"
        >
          <div className="flex items-center justify-between gap-2">
            <p aria-live="polite" className="pl-2 font-semibold text-foreground">
              {monthYear(first)}
            </p>
            <div className="flex gap-1">
              <button
                type="button"
                aria-label="Mes anterior"
                onClick={() => setFocused(addMonths(focused, -1))}
                className="rounded-full p-2 text-gray-500 transition hover:bg-gray-100 hover:text-foreground"
              >
                <ChevronLeft className="size-5" />
              </button>
              <button
                type="button"
                aria-label="Mes siguiente"
                onClick={() => setFocused(addMonths(focused, 1))}
                className="rounded-full p-2 text-gray-500 transition hover:bg-gray-100 hover:text-foreground"
              >
                <ChevronRight className="size-5" />
              </button>
            </div>
          </div>

          <div ref={gridRef} role="grid" aria-label={monthYear(first)} className="mt-3">
            <div role="row" className="grid grid-cols-7">
              {WEEKDAYS.map(([short, long]) => (
                <abbr
                  key={long}
                  role="columnheader"
                  title={long}
                  className="py-1 text-center text-xs font-semibold text-gray-400 no-underline"
                >
                  {short}
                </abbr>
              ))}
            </div>
            {Array.from({ length: cells.length / 7 }, (_, week) => (
              <div key={week} role="row" className="grid grid-cols-7">
                {cells.slice(week * 7, week * 7 + 7).map((date) => {
                  const inMonth = date.startsWith(month);
                  const selected = date === value;
                  const enabled = allowed(date);
                  return (
                    <div key={date} role="gridcell" aria-selected={selected} className="flex justify-center py-0.5">
                      <button
                        type="button"
                        data-date={date}
                        tabIndex={date === focused ? 0 : -1}
                        disabled={!enabled}
                        aria-label={`${weekdayDayMonth(date)} de ${date.slice(0, 4)}`}
                        aria-current={date === current ? "date" : undefined}
                        onClick={() => choose(date)}
                        className={`size-10 rounded-full text-sm transition outline-none focus-visible:ring-2 focus-visible:ring-brand-navy/40 ${
                          selected
                            ? "bg-brand-navy font-semibold text-white"
                            : !enabled
                              ? "cursor-not-allowed text-gray-300"
                              : date === current
                                ? "font-bold text-brand-navy hover:bg-brand-navy/10"
                                : inMonth
                                  ? "text-foreground hover:bg-gray-100"
                                  : "text-gray-400 hover:bg-gray-100"
                        }`}
                      >
                        {Number(date.slice(8))}
                      </button>
                    </div>
                  );
                })}
              </div>
            ))}
          </div>

          {allowed(current) && (
            <div className="mt-3 flex justify-end border-t border-gray-100 pt-3">
              <button
                type="button"
                onClick={() => choose(current)}
                className="rounded-full px-4 py-1.5 text-sm font-semibold text-brand-navy transition hover:bg-brand-navy/10"
              >
                Hoy
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function dayOfWeek(date: string): number {
  return new Date(`${date}T12:00:00Z`).getUTCDay();
}

function daysInMonth(month: string): number {
  const [year, number] = month.split("-").map(Number);
  return new Date(Date.UTC(year, number, 0)).getUTCDate();
}

/** El mismo día en otro mes; si ese mes es más corto, su último día. */
function addMonths(date: string, months: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(day, last));
  return target.toISOString().slice(0, 10);
}
