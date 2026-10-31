"use client";

import { BookOpen, CalendarDays, CircleAlert, LogOut, Menu, PieChart, X, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
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

/** Lo que dura el menú del celular en entrar o salir. */
const DRAWER_MS = 260;
/** En el panel del celular, cada bloque entra detrás del anterior: se desvanece y se corre un poco desde la izquierda. */
const ITEM_IN = "motion-safe:animate-[item-in_420ms_cubic-bezier(0.22,1,0.36,1)_both]";
const ITEM_DELAY_MS = 45;

/** Para que el botón de la barra de arriba, que pone cada pantalla, abra el menú del celular. */
const MenuContext = createContext<() => void>(() => {});

/**
 * El módulo administrativo de Dirección de Programa, con el mismo lenguaje que la app del estudiante: barra lateral
 * blanca con el escudo desde lg y, en el celular, ese mismo menú en un panel que sale del botón de la barra de arriba.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const [menu, setMenu] = useState<"closed" | "open" | "closing">("closed");

  useEffect(() => {
    if (menu !== "closing") return;
    const timer = window.setTimeout(() => setMenu("closed"), DRAWER_MS);
    return () => window.clearTimeout(timer);
  }, [menu]);

  return (
    <MenuContext.Provider value={() => setMenu("open")}>
      <div className="min-h-dvh bg-gray-100 lg:flex">
        <aside className="sticky top-0 hidden h-dvh w-72 shrink-0 flex-col border-r border-gray-200 bg-white px-4 py-6 lg:flex">
          <SidebarContent />
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">{children}</div>

        {menu !== "closed" && <MobileMenu closing={menu === "closing"} onClose={() => setMenu("closing")} />}
      </div>
    </MenuContext.Provider>
  );
}

/**
 * El escudo, las opciones y cerrar sesión: lo mismo en la barra lateral y en el panel del celular. Con stagger, cada
 * bloque entra animado detrás del anterior.
 */
function SidebarContent({ onNavigate, stagger = false }: { onNavigate?: () => void; stagger?: boolean }) {
  const pathname = usePathname();
  const router = useRouter();
  const active = (href: string) => pathname === href || pathname.startsWith(`${href}/`);
  const enter = stagger ? ITEM_IN : "";
  const delay = (step: number) => (stagger ? { animationDelay: `${110 + step * ITEM_DELAY_MS}ms` } : undefined);

  async function handleSignOut() {
    try {
      await signOut();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <>
      <div className={`flex items-center gap-3 px-2 ${enter}`} style={delay(0)}>
        <Escudo className="w-12" />
        <div>
          <p className="text-sm font-bold leading-tight text-brand-navy">
            Reservas
            <br />
            Unimagdalena
          </p>
          <p className="mt-1 text-xs font-medium text-gray-500">Dirección de Programa</p>
        </div>
      </div>
      <nav aria-label="Administración" className="mt-10 flex flex-col gap-1">
        {NAV.map(({ href, label, icon: Icon }, index) =>
          href ? (
            <Link
              key={label}
              href={href}
              onClick={onNavigate}
              aria-current={active(href) ? "page" : undefined}
              style={delay(index + 1)}
              className={`flex items-center gap-3 rounded-2xl px-4 py-3 font-semibold transition ${enter} ${
                active(href) ? "bg-brand-navy/10 text-brand-navy" : "text-gray-500 hover:bg-gray-100"
              }`}
            >
              <Icon className="size-5 shrink-0" />
              {label}
            </Link>
          ) : (
            <span
              key={label}
              title="Todavía no está disponible"
              style={delay(index + 1)}
              className={`flex cursor-not-allowed items-center gap-3 rounded-2xl px-4 py-3 font-semibold text-gray-300 ${enter}`}
            >
              <Icon className="size-5 shrink-0" />
              {label}
            </span>
          ),
        )}
      </nav>
      <button
        type="button"
        onClick={handleSignOut}
        style={delay(NAV.length + 1)}
        className={`mt-auto flex items-center gap-3 rounded-2xl px-4 py-3 font-semibold text-red-700 transition hover:bg-red-50 ${enter}`}
      >
        <LogOut className="size-5" /> Cerrar sesión
      </button>
    </>
  );
}

/**
 * El menú en el celular: un panel que entra desde la izquierda sobre la pantalla oscurecida. Se cierra con la X,
 * tocando afuera, con Escape o eligiendo una opción. Mientras está abierto, la página de atrás no se desplaza.
 */
function MobileMenu({ closing, onClose }: { closing: boolean; onClose: () => void }) {
  const panelRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") closeRef.current();
    }
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.style.overflow = previous;
      document.removeEventListener("keydown", closeOnEscape);
      opener?.focus();
    };
  }, []);

  return (
    <div className="fixed inset-0 z-50 lg:hidden">
      <div
        aria-hidden
        onClick={onClose}
        className={`absolute inset-0 bg-black/40 backdrop-blur-[2px] transition-opacity ${closing ? "opacity-0" : "motion-safe:animate-[fade-in_260ms_ease-out]"}`}
        style={{ transitionDuration: `${DRAWER_MS}ms` }}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label="Menú"
        tabIndex={-1}
        className={`absolute inset-y-0 left-0 flex w-80 max-w-[85vw] flex-col rounded-r-3xl bg-white px-4 py-6 shadow-2xl outline-none transition-transform ${
          closing ? "-translate-x-full" : "motion-safe:animate-[drawer-in_260ms_cubic-bezier(0.32,0.72,0,1)_both]"
        }`}
        style={{ transition: `transform ${DRAWER_MS}ms cubic-bezier(0.32,0.72,0,1)` }}
      >
        <button
          type="button"
          aria-label="Cerrar el menú"
          onClick={onClose}
          className="absolute right-3 top-5 rounded-full p-2 text-gray-500 transition hover:bg-gray-100 motion-safe:animate-[spin-in_420ms_cubic-bezier(0.22,1,0.36,1)_180ms_both]"
        >
          <X className="size-5" />
        </button>
        <SidebarContent onNavigate={onClose} stagger />
      </div>
    </div>
  );
}

/** La barra de arriba de cada pantalla del módulo, con el título y quién tiene la sesión; en el celular, el menú. */
export function AdminHeader({ title }: { title: string }) {
  const openMenu = useContext(MenuContext);
  const profile = useProfile();
  const initials = profile?.name
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("");
  return (
    <header className="sticky top-0 z-20 border-b border-gray-200 bg-white">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center gap-2 px-2 sm:px-6 lg:h-20 lg:gap-4 lg:px-10">
        <button
          type="button"
          aria-label="Abrir el menú"
          onClick={openMenu}
          className="rounded-full p-2.5 text-foreground transition hover:bg-gray-100 lg:hidden"
        >
          <Menu className="size-6" />
        </button>
        <h1 className="min-w-0 flex-1 truncate text-lg font-bold text-foreground sm:text-xl lg:text-2xl">{title}</h1>
        {profile && (
          <div className="flex shrink-0 items-center gap-3 pr-2 sm:pr-0">
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
