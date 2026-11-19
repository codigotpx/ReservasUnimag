import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import type { SearchResourcesResponse } from "../types";

/**
 * "21 a 40 de 47, página 2 de 3", con anterior y siguiente (UC1 FR-009 y FR-013). Con 20 o menos
 * recursos no hay control de paginación: el backend los manda en una sola página.
 */
export function Pagination({
  pagination,
  onPage,
}: {
  pagination: SearchResourcesResponse["pagination"];
  onPage: (page: number) => void;
}) {
  const { page, pageSize, totalPages, total } = pagination;
  if (totalPages <= 1) return null;
  const first = (page - 1) * pageSize + 1;
  const last = Math.min(page * pageSize, total);
  return (
    <nav aria-label="Páginas" className="flex items-center justify-between gap-3 pt-2">
      <PageButton disabled={page <= 1} onClick={() => onPage(page - 1)} label="Anterior">
        <ChevronLeft className="size-5" />
      </PageButton>
      <p className="text-center text-sm text-gray-500">
        <span className="font-semibold text-foreground">
          {first} a {last} de {total}
        </span>
        , página {page} de {totalPages}
      </p>
      <PageButton disabled={page >= totalPages} onClick={() => onPage(page + 1)} label="Siguiente">
        <ChevronRight className="size-5" />
      </PageButton>
    </nav>
  );
}

function PageButton({
  disabled,
  onClick,
  label,
  children,
}: {
  disabled: boolean;
  onClick: () => void;
  label: string;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="rounded-full border border-gray-200 bg-white p-3 text-brand-navy transition hover:bg-gray-50 disabled:text-gray-300"
    >
      {children}
    </button>
  );
}
