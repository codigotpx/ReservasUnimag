"use client";

import { BookOpen, CalendarDays, CircleAlert, ClipboardCheck, GraduationCap, LogOut, PieChart, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { signOut } from "@/features/auth/api";
import { useProfile } from "@/features/auth/session";
import { ROLE_LABELS } from "@/features/auth/types";

/** null = todavía sin pantalla: se muestra, pero no lleva a ningún lado. */
const NAV: { href: string | null; label: string; icon: LucideIcon; also?: string[] }[] = [
  { href: "/schedules", label: "Carga académica", icon: BookOpen, also: ["/schedules/format"] },
  { href: "/schedules/history", label: "Horarios importados", icon: CalendarDays },
  { href: "/occupancy", label: "Ocupación", icon: PieChart },
  { href: "/schedules/extraordinary", label: "Actividades extraordinarias", icon: CircleAlert },
  { href: "/pending-reviews", label: "Pendientes de check-out", icon: ClipboardCheck },
];

/**
 * El módulo administrativo de Dirección de Programa (diseño admin-importar-horarios): menú lateral oscuro en
 * escritorio y, en pantallas chicas, una barra arriba con lo mismo.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const router = useRouter();

  async function handleSignOut() {
    try {
      await signOut();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <div className="min-h-dvh bg-[#f4f6f8] lg:flex">
      <aside className="flex shrink-0 flex-col bg-brand-night text-white lg:sticky lg:top-0 lg:h-dvh lg:w-72">
        <div className="flex items-center gap-3 px-6 py-6">
          <span className="flex size-9 items-center justify-center rounded-lg bg-[#1e88ff]">
            <GraduationCap className="size-5" />
          </span>
          <div>
            <p className="font-bold leading-tight">Reservas Unimag</p>
            <p className="text-xs text-[#4da3ff]">Módulo Administrativo</p>
          </div>
        </div>
        {/* La ruta actual solo se conoce en el navegador; mientras tanto el menú sale sin nada resaltado. */}
        <Suspense fallback={<AdminNav pathname={null} />}>
          <CurrentAdminNav />
        </Suspense>
        <button
          type="button"
          onClick={handleSignOut}
          className="mt-auto hidden items-center gap-3 px-6 py-5 text-sm text-white/70 transition hover:text-white lg:flex"
        >
          <LogOut className="size-5" /> Cerrar sesión
        </button>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {children}
      </div>
    </div>
  );
}

function CurrentAdminNav() {
  return <AdminNav pathname={usePathname()} />;
}

function AdminNav({ pathname }: { pathname: string | null }) {
  // /schedules/history también empieza por /schedules: cada opción dice qué rutas le pertenecen.
  const active = (href: string, also: string[] = []) =>
    pathname !== null &&
    (href === "/schedules"
      ? [href, ...also].includes(pathname)
      : pathname === href || pathname.startsWith(`${href}/`));
  return (
    <nav aria-label="Administración" className="flex gap-1 overflow-x-auto px-2 pb-3 lg:mt-4 lg:flex-col lg:px-0">
      {NAV.map(({ href, label, icon: Icon, also }) =>
        href ? (
          <Link
            key={label}
            href={href}
            aria-current={active(href, also) ? "page" : undefined}
            className={`flex shrink-0 items-center gap-3 border-l-4 px-5 py-3 text-sm font-semibold transition ${
              active(href, also) ? "border-[#1e88ff] bg-[#004a87]/60" : "border-transparent text-white/70 hover:bg-white/5"
            }`}
          >
            <Icon className="size-5" />
            {label}
          </Link>
        ) : (
          <span
            key={label}
            title="Todavía no está disponible"
            className="flex shrink-0 cursor-not-allowed items-center gap-3 border-l-4 border-transparent px-5 py-3 text-sm text-white/35"
          >
            <Icon className="size-5" />
            {label}
          </span>
        ),
      )}
    </nav>
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
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-4 border-b border-[#dde3e9] bg-white px-4 sm:px-8 lg:px-12">
      <h1 className="truncate text-xl font-bold text-foreground">{title}</h1>
      {profile && (
        <div className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-full bg-brand-navy text-sm font-bold text-white">
            {initials}
          </span>
          <div className="hidden sm:block">
            <p className="text-sm font-bold text-foreground">{profile.name}</p>
            <p className="text-xs text-gray-500">{ROLE_LABELS[profile.role]}</p>
          </div>
        </div>
      )}
    </header>
  );
}
