"use client";

import { ChevronLeft, ChevronRight, FileClock, RefreshCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiRequestError, type ApiError } from "@/lib/api/client";
import { dateTimeLabel } from "@/lib/dates";
import { fetchUploads } from "../api";
import { uploadStatus } from "../labels";
import type { ScheduleUploadsResponse } from "../types";

type Outcome = { key: number; data: ScheduleUploadsResponse } | { key: number; error: ApiError };

/**
 * Horarios importados (UC3 Contratos §4): la constancia de FR-009, con cada carga, quién la subió y qué hizo. Una fila
 * lleva a su reporte completo.
 */
export function UploadHistory() {
  const router = useRouter();
  const [page, setPage] = useState(1);
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const key = page * 1000 + attempt;

  useEffect(() => {
    let active = true;
    fetchUploads(page)
      .then((data) => active && setOutcome({ key, data }))
      .catch((error) => {
        if (!active) return;
        const problem: ApiError =
          error instanceof ApiRequestError
            ? error.problem
            : { type: "about:blank", title: "Error", status: 0, detail: "No se pudo cargar el histórico." };
        if (problem.status === 401) {
          router.replace("/login");
          return;
        }
        setOutcome({ key, error: problem });
      });
    return () => {
      active = false;
    };
  }, [page, key, router]);

  const current = outcome?.key === key ? outcome : null;

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <section className="flex flex-col gap-5 rounded-lg border border-[#dde3e9] bg-white p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-foreground">Cargas de horario</h2>
            <p className="text-sm text-gray-500">
              Cada carga queda registrada aunque no se haya aplicado. Abre una para ver su reporte por fila.
            </p>
          </div>
          <Link
            href="/schedules"
            className="rounded-md bg-brand-navy px-5 py-2.5 text-sm font-bold text-white transition hover:bg-brand-navy/90"
          >
            Nueva carga
          </Link>
        </div>

        {!current ? (
          <div aria-busy="true" className="flex flex-col gap-2">
            {[0, 1, 2].map((index) => (
              <div key={index} className="h-14 animate-pulse rounded-lg bg-[#f4f6f8]" />
            ))}
          </div>
        ) : "error" in current ? (
          <div role="alert" className="flex flex-col items-center gap-3 py-10 text-center">
            <TriangleAlert className="size-9 text-amber-500" />
            <p className="max-w-sm text-gray-600">{current.error.detail}</p>
            <button
              type="button"
              onClick={() => setAttempt((value) => value + 1)}
              className="inline-flex items-center gap-2 rounded-md bg-brand-navy px-5 py-2.5 text-sm font-bold text-white"
            >
              <RefreshCw className="size-4" /> Reintentar
            </button>
          </div>
        ) : current.data.uploads.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-12 text-center text-gray-500">
            <FileClock className="size-10 text-gray-400" />
            <p>Todavía no se ha cargado ningún horario.</p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-lg border border-[#dde3e9]">
              <table className="w-full min-w-[56rem] text-left text-[13px]">
                <thead className="bg-[#f4f6f8] text-xs font-bold text-gray-500">
                  <tr>
                    <th className="px-4 py-3">Semestre</th>
                    <th className="px-4 py-3">Archivo</th>
                    <th className="px-4 py-3">Estado</th>
                    <th className="px-4 py-3">Validada</th>
                    <th className="px-4 py-3">Aplicada</th>
                    <th className="px-4 py-3">Resultado</th>
                    <th className="px-4 py-3">Subida por</th>
                  </tr>
                </thead>
                <tbody>
                  {current.data.uploads.map((upload) => {
                    const status = uploadStatus(upload.status, undefined, 0);
                    const href = `/schedules/history/${upload.uploadId}`;
                    return (
                      <tr
                        key={upload.uploadId}
                        onClick={() => router.push(href)}
                        className="cursor-pointer border-t border-[#dde3e9] transition hover:bg-[#f4f6f8]"
                      >
                        <td className="px-4 py-3 font-bold text-foreground">
                          <Link href={href} className="hover:underline" onClick={(event) => event.stopPropagation()}>
                            {upload.academicTerm}
                          </Link>
                        </td>
                        <td className="max-w-56 truncate px-4 py-3 text-foreground">{upload.fileName ?? "—"}</td>
                        <td className="px-4 py-3">
                          <span className={`whitespace-nowrap rounded px-2 py-1 text-[11px] font-bold ${status.className}`}>
                            {status.label}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-gray-600">{dateTimeLabel(upload.validatedAt)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-gray-600">
                          {upload.appliedAt ? dateTimeLabel(upload.appliedAt) : "—"}
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {upload.result
                            ? `${upload.result.blocksCreated} sesiones · ${upload.result.reservationsCancelled} reservas canceladas`
                            : "—"}
                        </td>
                        <td className="px-4 py-3 text-gray-600">{upload.uploadedBy.name ?? "—"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination
              {...current.data.pagination}
              onPage={(next) => {
                setPage(next);
                window.scrollTo({ top: 0 });
              }}
            />
          </>
        )}
      </section>
    </div>
  );
}

function Pagination({
  page,
  pageSize,
  totalPages,
  total,
  onPage,
}: ScheduleUploadsResponse["pagination"] & { onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  const first = (page - 1) * pageSize + 1;
  const last = Math.min(page * pageSize, total);
  return (
    <nav aria-label="Páginas" className="flex items-center justify-end gap-3 text-sm text-gray-500">
      {first}–{last} de {total}
      <button
        type="button"
        aria-label="Anterior"
        disabled={page <= 1}
        onClick={() => onPage(page - 1)}
        className="rounded-md border border-[#dde3e9] p-2 text-brand-navy disabled:text-gray-300"
      >
        <ChevronLeft className="size-4" />
      </button>
      <button
        type="button"
        aria-label="Siguiente"
        disabled={page >= totalPages}
        onClick={() => onPage(page + 1)}
        className="rounded-md border border-[#dde3e9] p-2 text-brand-navy disabled:text-gray-300"
      >
        <ChevronRight className="size-4" />
      </button>
    </nav>
  );
}
