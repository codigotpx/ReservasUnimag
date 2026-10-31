"use client";

import { CalendarDays, Search, User, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Escudo } from "./escudo";

const NAV: { href: string; label: string; icon: LucideIcon }[] = [
  { href: "/resources", label: "Buscar", icon: Search },
  { href: "/my-reservations", label: "Mis reservas", icon: CalendarDays },
  { href: "/profile", label: "Perfil", icon: User },
];

/** Navegación de la app: barra inferior en el celular y barra lateral desde lg. */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  return (
    <div className="min-h-dvh bg-gray-100 lg:flex">
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-gray-200 bg-white px-4 py-6 lg:flex">
        <div className="flex items-center gap-3 px-2">
          <Escudo className="w-12" />
          <p className="text-sm font-bold leading-tight text-brand-navy">
            Reservas
            <br />
            Unimagdalena
          </p>
        </div>
        <nav aria-label="Principal" className="mt-10 flex flex-col gap-1">
          {NAV.map(({ href, label, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              aria-current={isActive(href) ? "page" : undefined}
              className={`flex items-center gap-3 rounded-2xl px-4 py-3 font-semibold transition ${
                isActive(href) ? "bg-brand-navy/10 text-brand-navy" : "text-gray-500 hover:bg-gray-100"
              }`}
            >
              <Icon className="size-5" />
              {label}
            </Link>
          ))}
        </nav>
      </aside>

      <div className="flex min-h-dvh min-w-0 flex-1 flex-col pb-20 lg:pb-0">{children}</div>

      <nav
        aria-label="Principal"
        className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-3 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] lg:hidden"
      >
        {NAV.map(({ href, label, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            aria-current={isActive(href) ? "page" : undefined}
            className={`flex flex-col items-center gap-1 py-2.5 text-xs font-medium ${
              isActive(href) ? "text-brand-navy" : "text-gray-400"
            }`}
          >
            <Icon className="size-6" strokeWidth={isActive(href) ? 2.25 : 1.75} />
            {label}
          </Link>
        ))}
      </nav>
    </div>
  );
}
