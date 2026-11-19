import type { Category } from "../types";
import { CATEGORY_LABELS } from "../labels";

/** El selector Espacios / Activos de arriba de la pantalla. */
export function CategoryToggle({
  value,
  onChange,
  className = "",
}: {
  value: Category;
  onChange: (category: Category) => void;
  className?: string;
}) {
  return (
    <div role="radiogroup" aria-label="Tipo de recurso" className={`grid grid-cols-2 rounded-full bg-gray-200/80 p-1 ${className}`}>
      {(["ESPACIO", "ACTIVO"] as const).map((category) => (
        <button
          key={category}
          type="button"
          role="radio"
          aria-checked={value === category}
          onClick={() => onChange(category)}
          className={`h-11 rounded-full text-base font-semibold transition ${
            value === category ? "bg-brand-navy text-white shadow-sm" : "text-gray-500 hover:text-gray-700"
          }`}
        >
          {CATEGORY_LABELS[category]}
        </button>
      ))}
    </div>
  );
}
