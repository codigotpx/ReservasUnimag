"use client";

import { Calendar, CalendarSearch, RefreshCw, SearchX, SlidersHorizontal, TriangleAlert } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ConfirmReservation } from "@/features/reservations/components/confirm-reservation";
import { useQuota } from "@/features/reservations/use-quota";
import { useProfile } from "@/features/auth/session";
import { canReserve } from "@/features/auth/types";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { defaultSlot, shortDayLabel } from "@/lib/dates";
import { categoryOf, fromQuery, searchResources, toQuery } from "../api";
import { CATEGORY_LABELS } from "../labels";
import type { Category, ResourceWithStatus, SearchResourcesParams, SearchResourcesResponse } from "../types";
import { CategoryToggle } from "./category-toggle";
import { Pagination } from "./pagination";
import { QuotaCard } from "./quota-card";
import { ResourceCard } from "./resource-card";
import { ResourceFilters } from "./resource-filters";
import { ResourceListSkeleton } from "./resource-list-skeleton";
import { SanctionNotice } from "./sanction-notice";
import { TypeChips } from "./type-chips";

type Search = SearchResourcesParams & { category: Category };
type Outcome = { key: string; data: SearchResourcesResponse } | { key: string; error: ApiError };

/**
 * Consultar recursos (UC1 T040). En el celular se alterna entre el formulario y los resultados;
 * desde xl el formulario queda fijo a la izquierda y los resultados a la derecha.
 */
export function ResourcesScreen() {
  const router = useRouter();
  const query = useSearchParams();
  const profile = useProfile();

  const parsed = fromQuery(query);
  const search: Search | null = parsed ? { ...parsed, category: parsed.category ?? "ESPACIO" } : null;
  const searchKey = search ? toQuery(search) : null;

  const [editing, setEditing] = useState(false);
  const [chipsOpen, setChipsOpen] = useState(false);
  const [selected, setSelected] = useState<ResourceWithStatus | null>(null);
  const [listStale, setListStale] = useState(false);
  const reserver = profile !== null && canReserve(profile.role);
  const { quota, setQuota } = useQuota(reserver);
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const requestKey = searchKey && `${searchKey}#${attempt}`;

  useEffect(() => {
    if (!searchKey || !requestKey) return;
    let active = true;
    searchResources(fromQuery(new URLSearchParams(searchKey))!)
      .then((data) => active && setOutcome({ key: requestKey, data }))
      .catch((error) => {
        if (!active) return;
        const problem: ApiError =
          error instanceof ApiRequestError
            ? error.problem
            : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo consultar los recursos." };
        if (problem.status === 401) {
          router.replace("/login");
          return;
        }
        setOutcome({ key: requestKey, error: problem });
      });
    return () => {
      active = false;
    };
  }, [searchKey, requestKey, router]);

  const current = outcome && outcome.key === requestKey ? outcome : null;
  const loading = search !== null && current === null;
  const badRequest = current && "error" in current && current.error.status === 400 ? current.error.detail : undefined;
  const showForm = !search || editing || badRequest !== undefined;

  function go(next: Search) {
    setEditing(false);
    router.push(`/resources?${toQuery(next)}`, { scroll: true });
  }

  const formInitial: Search = search ?? {
    ...defaultSlot(),
    category: categoryOf(query.get("category")),
  };

  return (
    <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-5 sm:px-6 lg:px-10 lg:py-8 xl:grid xl:grid-cols-[minmax(20rem,26rem)_minmax(0,1fr)] xl:items-start xl:gap-10">
      <section aria-label="Filtros" className={`mx-auto w-full max-w-2xl xl:sticky xl:top-28 ${showForm ? "" : "hidden xl:block"}`}>
        <ResourceFilters key={`${searchKey ?? "nueva"}|${badRequest ?? ""}`} initial={formInitial} serverError={badRequest} onSubmit={go} />
      </section>

      <section aria-label="Resultados" className={`flex min-w-0 flex-col gap-5 mx-auto w-full max-w-2xl xl:max-w-none ${showForm ? "hidden xl:flex" : ""}`}>
        {!search ? (
          <Placeholder />
        ) : (
          <>
            <CategoryToggle
              className="xl:hidden"
              value={search.category}
              onChange={(category) =>
                category !== search.category &&
                go({ ...search, category, type: undefined, minCapacity: undefined, page: 1 })
              }
            />
            {/* Tocar la fecha abre el formulario; el ícono de la derecha muestra los tipos. */}
            <div className="flex h-14 items-center rounded-full border border-gray-200 bg-white xl:hidden">
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="flex h-full min-w-0 flex-1 items-center gap-3 rounded-l-full pl-5 text-left text-base text-foreground"
              >
                <Calendar className="size-6 shrink-0 text-brand-navy" />
                <span className="min-w-0 flex-1 truncate">
                  {shortDayLabel(search.date)} · {search.start} - {search.end} · {CATEGORY_LABELS[search.category]}
                </span>
              </button>
              <button
                type="button"
                aria-label="Filtrar por tipo"
                aria-expanded={chipsOpen}
                onClick={() => setChipsOpen((open) => !open)}
                className={`mx-1.5 rounded-full p-2.5 text-brand-navy transition ${chipsOpen ? "bg-brand-navy/10" : "hover:bg-gray-100"}`}
              >
                <SlidersHorizontal className="size-6" />
              </button>
            </div>
            <div className={chipsOpen ? "" : "hidden xl:block"}>
              <TypeChips
                category={search.category}
                value={search.type}
                onChange={(type) => go({ ...search, type, page: 1 })}
              />
            </div>
            {quota && <QuotaCard quota={quota} />}

            {loading ? (
              <ResourceListSkeleton />
            ) : current && "error" in current ? (
              current.error.status !== 400 && (
                <Problem detail={current.error.detail} onRetry={() => setAttempt((value) => value + 1)} />
              )
            ) : (
              current && (
                <Results
                  data={current.data}
                  onSearch={go}
                  search={search}
                  onSelect={reserver ? setSelected : undefined}
                />
              )
            )}
          </>
        )}
      </section>

      {selected && current && "data" in current && (
        <ConfirmReservation
          resource={selected}
          slot={current.data.timeSlot}
          sanctionNotice={current.data.sanctionNotice}
          quota={quota}
          onReserved={(reservation) => {
            setListStale(true);
            if (reservation.quota) setQuota(reservation.quota);
          }}
          onStale={() => setListStale(true)}
          onClose={() => {
            setSelected(null);
            // Tras reservar, o si el recurso resultó ocupado, la lista vuelve a pedirse.
            if (listStale) {
              setListStale(false);
              setAttempt((value) => value + 1);
            }
          }}
        />
      )}
    </div>
  );
}

function Results({
  data,
  search,
  onSearch,
  onSelect,
}: {
  data: SearchResourcesResponse;
  search: Search;
  onSearch: (next: Search) => void;
  onSelect?: (resource: ResourceWithStatus) => void;
}) {
  const activeFilters = Object.values(data.filters).filter((value) => value !== null).length;
  return (
    <>
      {data.sanctionNotice && <SanctionNotice notice={data.sanctionNotice} />}

      <div className="flex items-baseline justify-between gap-3 pt-1">
        <h2 className="text-lg font-bold text-foreground">Resultados sugeridos</h2>
        <span className="text-sm text-gray-500">Filtros activos ({activeFilters})</span>
      </div>

      {data.pagination.total === 0 ? (
        <Empty message={data.message ?? "Ningún recurso cumple los filtros."} />
      ) : (
        <>
          {!data.anySelectable && (
            <section role="status" className="rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-800">
              <p>{data.message}</p>
              {data.nextTimeSlot && (
                <button
                  type="button"
                  onClick={() => onSearch({ ...search, ...data.nextTimeSlot!, page: 1 })}
                  className="mt-3 inline-flex items-center gap-2 rounded-full bg-brand-navy px-4 py-2 font-semibold text-white"
                >
                  <CalendarSearch className="size-4" />
                  Ver de {data.nextTimeSlot.start} a {data.nextTimeSlot.end}
                </button>
              )}
            </section>
          )}
          <ul className="flex flex-col gap-4">
            {data.resources.map((resource) => (
              <li key={resource.id}>
                <ResourceCard
                  resource={resource}
                  slot={data.timeSlot}
                  onSelect={onSelect && (() => onSelect(resource))}
                />
              </li>
            ))}
          </ul>
          {data.catalogTruncated && (
            <p className="text-center text-sm text-gray-500">
              El inventario es muy grande y no se recorrió completo. Usa los filtros para acotar la búsqueda.
            </p>
          )}
          <Pagination pagination={data.pagination} onPage={(page) => onSearch({ ...search, page })} />
        </>
      )}
    </>
  );
}

function Placeholder() {
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl border border-dashed border-gray-300 px-6 py-16 text-center text-gray-500">
      <CalendarSearch className="size-10 text-gray-400" />
      <p className="max-w-sm">Elige la fecha, la franja y el tipo de recurso para ver qué está disponible.</p>
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl border border-gray-200 bg-white px-6 py-12 text-center text-gray-500">
      <SearchX className="size-10 text-gray-400" />
      <p className="max-w-sm">{message}</p>
    </div>
  );
}

/** El 503 del inventario (FR-008) o cualquier otra falla: nunca una lista vieja. */
function Problem({ detail, onRetry }: { detail: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-3xl border border-gray-200 bg-white px-6 py-12 text-center"
    >
      <TriangleAlert className="size-10 text-amber-500" />
      <p className="max-w-sm text-gray-600">{detail}</p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-2 rounded-full bg-brand-navy px-5 py-2.5 font-semibold text-white"
      >
        <RefreshCw className="size-4" /> Reintentar
      </button>
    </div>
  );
}
