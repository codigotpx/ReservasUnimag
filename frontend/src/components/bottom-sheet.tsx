"use client";

import { X } from "lucide-react";
import { useCallback, useEffect, useRef, type ReactNode } from "react";

/** Debajo de este ancho es hoja; desde ahí, diálogo centrado (el sm de Tailwind). */
const MOBILE = "(max-width: 639.98px)";
/** Cuánto hay que arrastrar, en proporción a la altura de la hoja, para que se cierre al soltar. */
const CLOSE_RATIO = 0.25;
/** O qué tan rápido, en px/ms: un gesto corto pero decidido también la cierra. */
const CLOSE_VELOCITY = 0.6;
const SLIDE_MS = 200;

/**
 * Hoja que sube desde abajo en el celular y diálogo centrado desde sm. En el celular se cierra arrastrándola hacia
 * abajo, desde la barrita o desde el contenido cuando ya está arriba del todo; en cualquier tamaño, con Escape,
 * tocando afuera o con la X. Bloquea el scroll de la página mientras está abierta.
 */
export function BottomSheet({
  label,
  onClose,
  footer,
  children,
}: {
  label: string;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
}) {
  const panelRef = useRef<HTMLDivElement>(null);
  const backdropRef = useRef<HTMLDivElement>(null);
  // Quien la abre suele pasar una función nueva en cada render; los efectos no deben repetirse por eso.
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  }, [onClose]);

  /** Cierra deslizando hacia abajo en el celular; en escritorio, de una vez. */
  const dismiss = useCallback(() => {
    const panel = panelRef.current;
    if (!panel || !window.matchMedia(MOBILE).matches) {
      closeRef.current();
      return;
    }
    slide(panel, backdropRef.current, "100%");
    window.setTimeout(() => closeRef.current(), SLIDE_MS);
  }, []);

  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") dismiss();
    }
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.style.overflow = previous;
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [dismiss]);

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;
    const mobile = window.matchMedia(MOBILE);
    let startY = 0;
    let startTime = 0;
    let offset = 0;
    let tracking = false;
    let dragging = false;

    function begin(y: number, time: number, target: EventTarget | null) {
      // Si el contenido ya bajó, el gesto es para subirlo, no para cerrar.
      tracking = mobile.matches && !scrolledInside(target, panel!);
      dragging = false;
      startY = y;
      startTime = time;
      offset = 0;
    }

    /** Devuelve true si el gesto es un arrastre de la hoja y hay que quitárselo al navegador. */
    function move(y: number): boolean {
      if (!tracking) return false;
      const delta = y - startY;
      if (!dragging) {
        if (delta < -6) tracking = false; // hacia arriba es scroll del contenido
        if (delta <= 6) return false;
        dragging = true;
      }
      offset = Math.max(0, delta);
      panel!.style.transition = "none";
      panel!.style.transform = `translateY(${offset}px)`;
      if (backdropRef.current) {
        backdropRef.current.style.transition = "none";
        backdropRef.current.style.opacity = String(Math.max(0, 1 - offset / panel!.offsetHeight));
      }
      return true;
    }

    function end(time: number) {
      const wasDragging = dragging;
      tracking = false;
      dragging = false;
      if (!wasDragging) return;
      const velocity = offset / Math.max(1, time - startTime);
      if (offset > panel!.offsetHeight * CLOSE_RATIO || velocity > CLOSE_VELOCITY) {
        dismiss();
      } else {
        slide(panel!, backdropRef.current, "0px");
      }
    }

    const onTouchStart = (event: TouchEvent) =>
      event.touches.length === 1 && begin(event.touches[0].clientY, event.timeStamp, event.target);
    const onTouchMove = (event: TouchEvent) => {
      if (move(event.touches[0].clientY)) event.preventDefault();
    };
    const onTouchEnd = (event: TouchEvent) => end(event.timeStamp);

    // Con mouse (o el modo dispositivo del navegador sin táctil) se arrastra solo desde la barrita.
    const handle = panel.querySelector<HTMLElement>("[data-sheet-handle]");
    const onPointerDown = (event: PointerEvent) => {
      if (event.pointerType !== "mouse") return;
      begin(event.clientY, event.timeStamp, null);
      const onPointerMove = (moveEvent: PointerEvent) => move(moveEvent.clientY);
      const onPointerUp = (upEvent: PointerEvent) => {
        end(upEvent.timeStamp);
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("pointerup", onPointerUp);
      };
      window.addEventListener("pointermove", onPointerMove);
      window.addEventListener("pointerup", onPointerUp);
    };

    panel.addEventListener("touchstart", onTouchStart, { passive: true });
    panel.addEventListener("touchmove", onTouchMove, { passive: false });
    panel.addEventListener("touchend", onTouchEnd);
    panel.addEventListener("touchcancel", onTouchEnd);
    handle?.addEventListener("pointerdown", onPointerDown);
    return () => {
      panel.removeEventListener("touchstart", onTouchStart);
      panel.removeEventListener("touchmove", onTouchMove);
      panel.removeEventListener("touchend", onTouchEnd);
      panel.removeEventListener("touchcancel", onTouchEnd);
      handle?.removeEventListener("pointerdown", onPointerDown);
    };
  }, [dismiss]);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6">
      <div
        ref={backdropRef}
        aria-hidden
        className="absolute inset-0 animate-[fade-in_200ms_ease-out] bg-black/40"
        onClick={dismiss}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        tabIndex={-1}
        className="relative flex max-h-[92dvh] w-full animate-[sheet-up_250ms_ease-out] flex-col rounded-t-[1.75rem] bg-white shadow-2xl outline-none sm:max-w-lg sm:animate-[fade-in_200ms_ease-out] sm:rounded-3xl"
      >
        <div
          data-sheet-handle
          aria-hidden
          className="flex h-7 shrink-0 cursor-grab touch-none items-center justify-center active:cursor-grabbing sm:hidden"
        >
          <span className="h-1.5 w-10 rounded-full bg-gray-200" />
        </div>
        <button
          type="button"
          aria-label="Cerrar"
          onClick={dismiss}
          className="absolute right-4 top-4 hidden rounded-full p-1.5 text-gray-500 transition hover:bg-gray-100 sm:block"
        >
          <X className="size-5" />
        </button>
        <div className="overflow-y-auto overscroll-contain px-6 pb-6 pt-1 sm:pt-7">{children}</div>
        {footer && (
          <div className="shrink-0 border-t border-gray-200 px-6 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-4">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

/** Lleva la hoja a una posición con transición, y el fondo a su opacidad correspondiente. */
function slide(panel: HTMLElement, backdrop: HTMLElement | null, to: "0px" | "100%") {
  panel.style.transition = `transform ${SLIDE_MS}ms ease-out`;
  panel.style.transform = `translateY(${to})`;
  if (backdrop) {
    backdrop.style.transition = `opacity ${SLIDE_MS}ms ease-out`;
    backdrop.style.opacity = to === "0px" ? "1" : "0";
  }
}

/** Si entre el punto tocado y la hoja hay algo con scroll que ya bajó. */
function scrolledInside(target: EventTarget | null, panel: HTMLElement): boolean {
  for (let node = target as HTMLElement | null; node && node !== panel; node = node.parentElement) {
    if (node.scrollTop > 0) return true;
  }
  return false;
}
