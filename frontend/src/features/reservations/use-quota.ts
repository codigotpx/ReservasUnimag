"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchQuota } from "./api";
import type { Quota } from "./types";

/**
 * El cupo de reservas vigentes (UC2 FR-008), compartido por la tarjeta de cupo y la hoja de confirmación.
 * Si no se puede leer queda en null: es información de apoyo y el backend decide al confirmar.
 */
export function useQuota(enabled: boolean): { quota: Quota | null; setQuota: (quota: Quota) => void; reload: () => void } {
  const [quota, setQuota] = useState<Quota | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    fetchQuota()
      .then((value) => active && setQuota(value))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [enabled, attempt]);

  const reload = useCallback(() => setAttempt((value) => value + 1), []);
  return { quota: enabled ? quota : null, setQuota, reload };
}
