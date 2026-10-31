import { apiFetch } from "@/lib/api/client";
import type { Profile } from "./types";

/** POST /api/auth/login (UC1 Contratos §4): 204 y la sesión queda en una cookie httpOnly. */
export function signIn(email: string, password: string): Promise<void> {
  return apiFetch<void>("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
}

/** GET /api/auth/me: quién tiene la sesión, o 401 si no hay. */
export function fetchProfile(): Promise<Profile> {
  return apiFetch<Profile>("/auth/me");
}

/** POST /api/auth/logout: 204 y la cookie borrada. */
export function signOut(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}
