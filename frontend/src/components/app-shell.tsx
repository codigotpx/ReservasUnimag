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
  const activeIndex = NAV.findIndex(({ href }) => isActive(href));

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

      {/* La key hace que cada pestaña entre con su animación en vez de aparecer de golpe. */}
      <div
        key={pathname}
        className="flex min-h-dvh min-w-0 flex-1 flex-col pb-20 motion-safe:animate-[page-in_320ms_cubic-bezier(0.22,1,0.36,1)] lg:pb-0"
      >
        {children}
      </div>

      <nav
        aria-label="Principal"
        className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-3 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] lg:hidden"
      >
        {/* La píldora detrás del ícono activo se desliza de una pestaña a otra, con un leve rebote al llegar. */}
        <span
          aria-hidden
          className={`pointer-events-none absolute left-0 top-1.5 flex h-8 w-1/3 justify-center motion-safe:transition-[transform,opacity] motion-safe:duration-500 motion-safe:ease-[cubic-bezier(0.34,1.45,0.64,1)] ${
            activeIndex < 0 ? "opacity-0" : "opacity-100"
          }`}
          style={{ transform: `translateX(${Math.max(activeIndex, 0) * 100}%)` }}
        >
          <span className="h-8 w-16 rounded-full bg-brand-navy/10" />
        </span>
        {NAV.map(({ href, label, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            aria-current={isActive(href) ? "page" : undefined}
            className={`group relative flex flex-col items-center gap-1 py-2.5 text-xs font-medium transition-colors duration-300 ${
              isActive(href) ? "text-brand-navy" : "text-gray-400"
            }`}
          >
            <Icon
              className={`size-6 motion-safe:transition-transform motion-safe:duration-300 motion-safe:ease-[cubic-bezier(0.34,1.45,0.64,1)] motion-safe:group-active:scale-90 ${
                isActive(href) ? "motion-safe:-translate-y-px motion-safe:scale-105" : ""
              }`}
              strokeWidth={isActive(href) ? 2.25 : 1.75}
            />
            {label}
          </Link>
        ))}
      </nav>
    </div>
  );
}
