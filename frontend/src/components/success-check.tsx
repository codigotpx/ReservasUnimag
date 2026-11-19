"use client";

import { useEffect } from "react";

/**
 * El check de éxito animado: el disco aparece con un leve rebote y luego se dibujan el círculo y el check. Con
 * "reducir movimiento" se ve el check completo, quieto. En los celulares que lo permiten, una vibración corta
 * acompaña el momento en que se cierra el check.
 */
export function SuccessCheck() {
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const timer = window.setTimeout(() => navigator.vibrate?.(14), 800);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <span
      aria-hidden
      className="flex size-20 items-center justify-center rounded-full bg-green-50 motion-safe:animate-[pop_500ms_cubic-bezier(0.34,1.4,0.64,1)_both]"
    >
      <svg
        viewBox="0 0 52 52"
        className="size-16 text-green-600"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <circle
          cx="26"
          cy="26"
          r="23"
          strokeWidth="2.5"
          pathLength={1}
          strokeDasharray="1"
          transform="rotate(-90 26 26)"
          className="motion-safe:animate-[draw_550ms_cubic-bezier(0.65,0,0.35,1)_120ms_both]"
        />
        <path
          d="M16 27 l7 7 l13 -15"
          strokeWidth="3.5"
          pathLength={1}
          strokeDasharray="1"
          className="motion-safe:animate-[draw_360ms_cubic-bezier(0.65,0,0.35,1)_480ms_both]"
        />
      </svg>
    </span>
  );
}
