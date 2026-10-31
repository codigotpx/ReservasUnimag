"use client";

import { useSyncExternalStore, type ReactNode } from "react";

const subscribe = () => () => {};

/**
 * Dibuja sus hijos solo en el navegador. Para lo que depende de la hora actual, que Next.js no deja usar al
 * pre-renderizar (cacheComponents).
 */
export function ClientOnly({ children, fallback = null }: { children: ReactNode; fallback?: ReactNode }) {
  const inBrowser = useSyncExternalStore(subscribe, () => true, () => false);
  return inBrowser ? children : fallback;
}
