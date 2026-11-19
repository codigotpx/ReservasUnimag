"use client";

import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";

export interface DropdownOption {
  value: string;
  label: string;
}

/**
 * Lista desplegable propia, para no depender del <select> nativo, que se ve distinto en cada navegador.
 * Se maneja con teclado como un listbox: flechas, Inicio, Fin, Enter y Escape.
 */
export function Dropdown({
  label,
  value,
  options,
  onChange,
  icon,
  className = "",
}: {
  /** Nombre accesible; la etiqueta visible la pone quien lo usa. */
  label: string;
  value: string;
  options: DropdownOption[];
  onChange: (value: string) => void;
  icon?: ReactNode;
  className?: string;
}) {
  const id = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  );
  const selected = options[selectedIndex];

  useEffect(() => {
    if (!open) return;
    function closeOnOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", closeOnOutside);
    return () => document.removeEventListener("pointerdown", closeOnOutside);
  }, [open]);

  useEffect(() => {
    if (open) {
      listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
    }
  }, [open, active]);

  function show() {
    setActive(selectedIndex);
    setOpen(true);
  }

  function choose(index: number) {
    onChange(options[index].value);
    setOpen(false);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const last = options.length - 1;
    const moves: Record<string, () => void> = {
      ArrowDown: () => (open ? setActive((index) => Math.min(last, index + 1)) : show()),
      ArrowUp: () => (open ? setActive((index) => Math.max(0, index - 1)) : show()),
      Home: () => open && setActive(0),
      End: () => open && setActive(last),
      Enter: () => (open ? choose(active) : show()),
      " ": () => (open ? choose(active) : show()),
      Escape: () => setOpen(false),
      Tab: () => setOpen(false),
    };
    const move = moves[event.key];
    if (!move) return;
    if (event.key !== "Tab") event.preventDefault();
    move();
  }

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      <button
        type="button"
        role="combobox"
        aria-label={label}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-activedescendant={open ? `${id}-${active}` : undefined}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={handleKeyDown}
        className={`flex h-12 w-full items-center gap-3 rounded-full border bg-white px-4 text-left text-base text-foreground outline-none transition ${
          open ? "border-brand-navy ring-2 ring-brand-navy/20" : "border-gray-200 hover:border-gray-300"
        } focus-visible:border-brand-navy focus-visible:ring-2 focus-visible:ring-brand-navy/20`}
      >
        {icon && <span className="shrink-0 text-gray-500">{icon}</span>}
        <span className="min-w-0 flex-1 truncate">{selected?.label}</span>
        <ChevronDown
          className={`size-5 shrink-0 text-gray-500 transition-transform ${open ? "rotate-180" : ""}`}
        />
      </button>

      {open && (
        <ul
          ref={listRef}
          id={`${id}-list`}
          role="listbox"
          aria-label={label}
          className="absolute inset-x-0 top-full z-40 mt-2 max-h-64 overflow-y-auto rounded-2xl border border-gray-200 bg-white p-1.5 shadow-xl"
        >
          {options.map((option, index) => {
            const isSelected = option.value === value;
            return (
              <li
                key={option.value}
                id={`${id}-${index}`}
                data-index={index}
                role="option"
                aria-selected={isSelected}
                onPointerEnter={() => setActive(index)}
                onClick={() => choose(index)}
                className={`flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2.5 text-base ${
                  index === active ? "bg-gray-100" : ""
                } ${isSelected ? "font-semibold text-brand-navy" : "text-foreground"}`}
              >
                <span className="flex-1">{option.label}</span>
                {isSelected && <Check className="size-4 shrink-0" />}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
