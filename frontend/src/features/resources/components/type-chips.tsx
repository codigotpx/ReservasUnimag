import { TYPES_BY_CATEGORY, codeLabel } from "../labels";
import type { Category } from "../types";

/** Atajo para filtrar por tipo sin abrir el formulario. */
export function TypeChips({
  category,
  value,
  onChange,
}: {
  category: Category;
  value?: string;
  onChange: (type: string | undefined) => void;
}) {
  const options: { code?: string; label: string }[] = [
    { label: "Todos" },
    ...TYPES_BY_CATEGORY[category].map((code) => ({ code, label: codeLabel(code) })),
  ];
  return (
    <div className="flex flex-wrap gap-2 sm:gap-3">
      {options.map(({ code, label }) => {
        const selected = code === value;
        return (
          <button
            key={label}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(code)}
            className={`h-10 rounded-full border px-4 text-base transition sm:h-11 sm:px-5 ${
              selected
                ? "border-brand-navy bg-brand-navy text-white"
                : "border-gray-200 bg-white text-gray-500 hover:border-gray-300"
            }`}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
