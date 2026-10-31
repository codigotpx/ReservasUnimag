"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useProfile } from "./session";
import { homeFor, type Role } from "./types";

/**
 * Manda a su propia pantalla de inicio a quien no tiene el rol. Es por usabilidad: quien decide es el backend,
 * que responde 403 a cualquier otro rol.
 */
export function RoleGate({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const router = useRouter();
  const profile = useProfile();
  const denied = profile !== null && !allow.includes(profile.role);

  useEffect(() => {
    if (denied) router.replace(homeFor(profile.role));
  }, [denied, profile, router]);

  return denied ? null : children;
}
