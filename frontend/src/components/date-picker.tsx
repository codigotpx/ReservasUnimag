"use client";

import { Calendar, ChevronDown, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { addDays, today, weekdayDayMonth } from "@/lib/dates";

const WEEKDAYS: [short: string, long: string][] = [
  ["L", "lunes"],
  ["M", "martes"],
  ["M", "miércoles"],
  ["J", "jueves"],
  ["V", "viernes"],
  ["S", "sábado"],
  ["D", "domingo"],
];

const monthFormat = new Intl.DateTimeFormat("es-CO", { month: "long", timeZone: "UTC" });
const MONTHS = Array.from({ length: 12 }, (_, index) => {
  const name = monthFormat.format(new Date(Date.UTC(2000, index, 15)));
  return `${name.charAt(0).toUpperCase()}${name.slice(1)}`;
});

/** Cuántos años se ven a la vez en la vista de años. */
const YEARS_PER_PAGE = 12;

type View = "days" | "months" | "years";

/**
 * Selector de fecha propio, para no depender del calendario nativo, que se ve distinto en cada navegador y no sigue
 * el diseño. Trabaja con fechas yyyy-MM-dd. El mes y el año de arriba se tocan para saltar por meses o por años.
 * Con teclado: flechas, Inicio y Fin de la semana, Re Pág y Av Pág para cambiar de mes, Enter para elegir y Escape
 * para volver a los días o cerrar.
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
  const bodyRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [view, setView] = useState<View>("days");
  const [focused, setFocused] = useState(() => value || today());
  const current = today();
  const allowed = (date: string) => !min || date >= min;

  const year = Number(focused.slice(0, 4));
  const monthIndex = Number(focused.slice(5, 7)) - 1;
  const month = focused.slice(0, 7);
  const firstYear = Math.floor(year / YEARS_PER_PAGE) * YEARS_PER_PAGE;

  useEffect(() => {
    if (!open) return;
    function closeOnOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", closeOnOutside);
    return () => document.removeEventListener("pointerdown", closeOnOutside);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const key = { days: focused, months: focused.slice(0, 7), years: focused.slice(0, 4) }[view];
    bodyRef.current?.querySelector<HTMLButtonElement>(`[data-key="${key}"]`)?.focus();
  }, [open, view, focused]);

  function show() {
    setFocused(value || (min && min > current ? min : current));
    setView("days");
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

  function chooseMonth(index: number) {
    setFocused(addMonths(focused, index - monthIndex));
    setView("days");
  }

  function chooseYear(target: number) {
    setFocused(addMonths(focused, (target - year) * 12));
    setView("months");
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      if (view === "days") close();
      else setView("days");
      return;
    }
    const weekday = (dayOfWeek(focused) + 6) % 7;
    const moves: Record<View, Record<string, () => string>> = {
      days: {
        ArrowLeft: () => addDays(focused, -1),
        ArrowRight: () => addDays(focused, 1),
        ArrowUp: () => addDays(focused, -7),
        ArrowDown: () => addDays(focused, 7),
        Home: () => addDays(focused, -weekday),
        End: () => addDays(focused, 6 - weekday),
        PageUp: () => addMonths(focused, -1),
        PageDown: () => addMonths(focused, 1),
      },
      // Los meses y los años van en tres columnas.
      months: {
        ArrowLeft: () => addMonths(focused, -1),
        ArrowRight: () => addMonths(focused, 1),
        ArrowUp: () => addMonths(focused, -3),
        ArrowDown: () => addMonths(focused, 3),
      },
      years: {
        ArrowLeft: () => addMonths(focused, -12),
        ArrowRight: () => addMonths(focused, 12),
        ArrowUp: () => addMonths(focused, -36),
        ArrowDown: () => addMonths(focused, 36),
      },
    };
    const move = moves[view][event.key];
    if (!move) return;
    event.preventDefault();
    setFocused(move());
  }

  // Las flechas de arriba avanzan un mes, un año o una página de años, según la vista.
  const step = { days: 1, months: 12, years: 12 * YEARS_PER_PAGE }[view];
  const [previousLabel, nextLabel] = {
    days: ["Mes anterior", "Mes siguiente"],
    months: ["Año anterior", "Año siguiente"],
    years: ["Años anteriores", "Años siguientes"],
  }[view];

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
          className="absolute left-0 top-full z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] select-none rounded-2xl border border-gray-200 bg-white p-4 shadow-xl"
        >
          <div className="flex items-center justify-between gap-2">
            <div aria-live="polite" className="flex items-center">
              {view === "days" && (
                <HeaderButton label="Elegir mes" onClick={() => setView("months")}>
                  {MONTHS[monthIndex]}
                </HeaderButton>
              )}
              {view !== "years" ? (
                <HeaderButton label="Elegir año" onClick={() => setView("years")}>
                  {year}
                </HeaderButton>
              ) : (
                <p className="px-2 py-1 font-semibold text-foreground">
                  {firstYear} – {firstYear + YEARS_PER_PAGE - 1}
                </p>
              )}
            </div>
            <div className="flex gap-1">
              <button
                type="button"
                aria-label={previousLabel}
                onClick={() => setFocused(addMonths(focused, -step))}
                className="rounded-full p-2 text-gray-500 transition hover:bg-gray-100 hover:text-foreground"
              >
                <ChevronLeft className="size-5" />
              </button>
              <button
                type="button"
                aria-label={nextLabel}
                onClick={() => setFocused(addMonths(focused, step))}
                className="rounded-full p-2 text-gray-500 transition hover:bg-gray-100 hover:text-foreground"
              >
                <ChevronRight className="size-5" />
              </button>
            </div>
          </div>

          <div ref={bodyRef} className="mt-3">
            {view === "days" && (
              <DaysGrid
                month={month}
                focused={focused}
                value={value}
                current={current}
                allowed={allowed}
                onChoose={choose}
              />
            )}

            {view === "months" && (
              <div role="listbox" aria-label={`Meses de ${year}`} className="grid grid-cols-3 gap-1">
                {MONTHS.map((name, index) => {
                  const key = `${year}-${String(index + 1).padStart(2, "0")}`;
                  // Un mes se puede elegir si su último día todavía cabe después del mínimo.
                  const enabled = allowed(`${key}-${String(daysInMonth(key)).padStart(2, "0")}`);
                  return (
                    <PeriodButton
                      key={key}
                      dataKey={key}
                      focused={key === month}
                      selected={value.startsWith(key)}
                      current={current.startsWith(key)}
                      enabled={enabled}
                      onClick={() => chooseMonth(index)}
                    >
                      {name}
                    </PeriodButton>
                  );
                })}
              </div>
            )}

            {view === "years" && (
              <div role="listbox" aria-label="Años" className="grid grid-cols-3 gap-1">
                {Array.from({ length: YEARS_PER_PAGE }, (_, index) => {
                  const candidate = firstYear + index;
                  return (
                    <PeriodButton
                      key={candidate}
                      dataKey={String(candidate)}
                      focused={candidate === year}
                      selected={value.startsWith(String(candidate))}
                      current={current.startsWith(String(candidate))}
                      enabled={allowed(`${candidate}-12-31`)}
                      onClick={() => chooseYear(candidate)}
                    >
                      {candidate}
                    </PeriodButton>
                  );
                })}
              </div>
            )}
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

function HeaderButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex items-center gap-0.5 rounded-full px-2 py-1 font-semibold text-foreground transition hover:bg-gray-100"
    >
      {children}
      <ChevronDown className="size-4 text-gray-400" />
    </button>
  );
}

function DaysGrid({
  month,
  focused,
  value,
  current,
  allowed,
  onChoose,
}: {
  month: string;
  focused: string;
  value: string;
  current: string;
  allowed: (date: string) => boolean;
  onChoose: (date: string) => void;
}) {
  const first = `${month}-01`;
  const lead = (dayOfWeek(first) + 6) % 7;
  const cells = Array.from({ length: Math.ceil((lead + daysInMonth(month)) / 7) * 7 }, (_, index) =>
    addDays(first, index - lead),
  );

  return (
    <div role="grid" aria-label={`${MONTHS[Number(month.slice(5)) - 1]} ${month.slice(0, 4)}`}>
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
            const selected = date === value;
            const enabled = allowed(date);
            return (
              <div key={date} role="gridcell" aria-selected={selected} className="flex justify-center py-0.5">
                <button
                  type="button"
                  data-key={date}
                  tabIndex={date === focused ? 0 : -1}
                  disabled={!enabled}
                  aria-label={`${weekdayDayMonth(date)} de ${date.slice(0, 4)}`}
                  aria-current={date === current ? "date" : undefined}
                  onClick={() => onChoose(date)}
                  className={`size-10 rounded-full text-sm transition outline-none focus-visible:ring-2 focus-visible:ring-brand-navy/40 ${
                    selected
                      ? "bg-brand-navy font-semibold text-white"
                      : !enabled
                        ? "cursor-not-allowed text-gray-300"
                        : date === current
                          ? "font-bold text-brand-navy hover:bg-brand-navy/10"
                          : date.startsWith(month)
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
  );
}

/** Un mes o un año en sus grillas de tres columnas. */
function PeriodButton({
  dataKey,
  focused,
  selected,
  current,
  enabled,
  onClick,
  children,
}: {
  dataKey: string;
  focused: boolean;
  selected: boolean;
  current: boolean;
  enabled: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={selected}
      data-key={dataKey}
      tabIndex={focused ? 0 : -1}
      disabled={!enabled}
      onClick={onClick}
      className={`h-12 rounded-full text-sm transition outline-none focus-visible:ring-2 focus-visible:ring-brand-navy/40 ${
        selected
          ? "bg-brand-navy font-semibold text-white"
          : !enabled
            ? "cursor-not-allowed text-gray-300"
            : current
              ? "font-bold text-brand-navy hover:bg-brand-navy/10"
              : "text-foreground hover:bg-gray-100"
      }`}
    >
      {children}
    </button>
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
