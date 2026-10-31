"use client";

import { useRouter } from "next/navigation";
import { createContext, use, useEffect, useState, type ReactNode } from "react";
import { ApiRequestError } from "@/lib/api/client";
import { fetchProfile } from "./api";
import type { Profile } from "./types";

const SessionContext = createContext<Profile | null>(null);

/**
 * Comprueba la sesión del grupo (app) (UC1 T015) y, sin cookie válida, manda al login.
 * La pantalla se dibuja mientras tanto, sin esperar: quien necesite el perfil lo recibe cuando llega.
 * La comprobación que manda es la del backend, en cada petición.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);

  useEffect(() => {
    let active = true;
    fetchProfile()
      .then((value) => active && setProfile(value))
      .catch((error) => {
        if (active && error instanceof ApiRequestError && error.problem.status === 401) {
          router.replace("/login");
        }
      });
    return () => {
      active = false;
    };
  }, [router]);

  return <SessionContext value={profile}>{children}</SessionContext>;
}

/** El perfil de quien tiene la sesión, o null mientras se comprueba. */
export function useProfile(): Profile | null {
  return use(SessionContext);
}
