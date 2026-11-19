"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { signOut } from "../api";
import { useProfile } from "../session";
import { ROLE_LABELS } from "../types";

export function ProfileCard() {
  const profile = useProfile();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  async function handleSignOut() {
    setLeaving(true);
    try {
      await signOut();
    } finally {
      router.replace("/login");
    }
  }

  if (!profile) {
    return <section aria-busy="true" className="h-48 animate-pulse rounded-3xl border border-gray-200 bg-white" />;
  }

  return (
    <section className="rounded-3xl border border-gray-200 bg-white p-6">
      <p className="text-xl font-bold text-foreground">{profile.name}</p>
      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
        <dt className="text-gray-500">Código</dt>
        <dd className="font-medium">{profile.code}</dd>
        <dt className="text-gray-500">Rol</dt>
        <dd className="font-medium">{ROLE_LABELS[profile.role]}</dd>
      </dl>
      <button
        type="button"
        onClick={handleSignOut}
        disabled={leaving}
        className="mt-6 inline-flex h-11 items-center gap-2 rounded-full border border-gray-200 px-5 font-semibold text-red-700 transition hover:bg-red-50 disabled:opacity-60"
      >
        <LogOut className="size-5" />
        {leaving ? "Cerrando sesión…" : "Cerrar sesión"}
      </button>
    </section>
  );
}
