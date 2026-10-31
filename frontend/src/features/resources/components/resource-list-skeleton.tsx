/** Mientras llega la respuesta: la misma silueta de las tarjetas (skeleton-loading en Figma). */
export function ResourceListSkeleton() {
  return (
    <div aria-busy="true" aria-label="Cargando recursos" className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <Bar className="h-7 w-36" />
        <Bar className="h-5 w-24" />
      </div>
      {[0, 1, 2].map((card) => (
        <div key={card} className="rounded-3xl border border-gray-200 bg-white p-5 sm:p-6">
          <div className="flex justify-between gap-6">
            <Bar className="h-8 w-3/5" />
            <Bar className="h-10 w-24 bg-gray-200/70" />
          </div>
          <Bar className="mt-4 h-6 w-4/5 bg-gray-200/70" />
          <div className="mt-4 flex items-center gap-3">
            <Bar className="size-8" />
            <Bar className="h-6 w-2/5" />
          </div>
        </div>
      ))}
    </div>
  );
}

function Bar({ className }: { className: string }) {
  return <div className={`animate-pulse rounded-lg bg-gray-200 ${className}`} />;
}
