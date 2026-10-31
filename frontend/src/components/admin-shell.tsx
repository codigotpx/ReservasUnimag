"use client";

import { BookOpen, CalendarDays, CircleAlert, LogOut, PieChart, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { signOut } from "@/features/auth/api";
import { useProfile } from "@/features/auth/session";
import { ROLE_LABELS } from "@/features/auth/types";
import { Escudo } from "./escudo";

/** null = todavía sin pantalla: se muestra, pero no lleva a ningún lado. */
const NAV: { href: string | null; label: string; icon: LucideIcon }[] = [
  { href: "/schedules", label: "Carga académica", icon: BookOpen },
  { href: null, label: "Horarios importados", icon: CalendarDays },
  { href: null, label: "Ocupación", icon: PieChart },
  { href: null, label: "Actividades extraordinarias", icon: CircleAlert },
];

/**
 * El módulo administrativo de Dirección de Programa, con el mismo lenguaje que la app del estudiante: barra lateral
 * blanca con el escudo desde lg y, en pantallas chicas, el escudo y el menú arriba.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const active = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  async function handleSignOut() {
    try {
      await signOut();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <div className="min-h-dvh bg-gray-100 lg:flex">
      <aside className="flex shrink-0 flex-col border-b border-gray-200 bg-white px-4 pt-4 lg:sticky lg:top-0 lg:h-dvh lg:w-72 lg:border-b-0 lg:border-r lg:py-6">
        <div className="flex items-center gap-3 px-2">
          <Escudo className="w-10 lg:w-12" />
          <div>
            <p className="text-sm font-bold leading-tight text-brand-navy">
              Reservas
              <br />
              Unimagdalena
            </p>
            <p className="mt-1 text-xs font-medium text-gray-500">Dirección de Programa</p>
          </div>
        </div>
        <nav
          aria-label="Administración"
          className="-mx-4 mt-4 flex gap-1 overflow-x-auto px-4 pb-3 [scrollbar-width:none] lg:mx-0 lg:mt-10 lg:flex-col lg:px-0 lg:pb-0"
        >
          {NAV.map(({ href, label, icon: Icon }) =>
            href ? (
              <Link
                key={label}
                href={href}
                aria-current={active(href) ? "page" : undefined}
                className={`flex shrink-0 items-center gap-3 rounded-2xl px-4 py-3 font-semibold transition ${
                  active(href) ? "bg-brand-navy/10 text-brand-navy" : "text-gray-500 hover:bg-gray-100"
                }`}
              >
                <Icon className="size-5" />
                {label}
              </Link>
            ) : (
              <span
                key={label}
                title="Todavía no está disponible"
                className="flex shrink-0 cursor-not-allowed items-center gap-3 rounded-2xl px-4 py-3 font-semibold text-gray-300"
              >
                <Icon className="size-5" />
                {label}
              </span>
            ),
          )}
        </nav>
        <button
          type="button"
          onClick={handleSignOut}
          className="mt-auto hidden items-center gap-3 rounded-2xl px-4 py-3 font-semibold text-red-700 transition hover:bg-red-50 lg:flex"
        >
          <LogOut className="size-5" /> Cerrar sesión
        </button>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  );
}

/** La barra de arriba de cada pantalla del módulo, con el título y quién tiene la sesión. */
export function AdminHeader({ title }: { title: string }) {
  const profile = useProfile();
  const initials = profile?.name
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("");
  return (
    <header className="sticky top-0 z-20 border-b border-gray-200 bg-white">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6 lg:h-20 lg:px-10">
        <h1 className="truncate text-xl font-bold text-foreground lg:text-2xl">{title}</h1>
        {profile && (
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-full bg-brand-navy/10 text-sm font-bold text-brand-navy">
              {initials}
            </span>
            <div className="hidden sm:block">
              <p className="text-sm font-semibold text-foreground">{profile.name}</p>
              <p className="text-xs text-gray-500">{ROLE_LABELS[profile.role]}</p>
            </div>
          </div>
        )}
      </div>
    </header>
  );
}
