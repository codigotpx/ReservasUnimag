import { Bell } from "lucide-react";
import { Escudo } from "./escudo";

/** La barra blanca de arriba: escudo y título en el celular; desde lg el escudo ya está en la barra lateral. */
export function PageHeader({ title }: { title: string }) {
  return (
    <header className="sticky top-0 z-20 border-b border-gray-200 bg-white">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center gap-3 px-4 sm:px-6 lg:h-20 lg:px-10">
        <Escudo className="w-9 lg:hidden" />
        <h1 className="text-xl font-bold text-foreground lg:text-2xl">{title}</h1>
        <button
          type="button"
          aria-label="Notificaciones"
          className="ml-auto rounded-full p-2 text-foreground transition hover:bg-gray-100"
        >
          <Bell className="size-6" />
        </button>
      </div>
    </header>
  );
}
