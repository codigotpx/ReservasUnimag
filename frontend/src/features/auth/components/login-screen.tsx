"use client";

import Image, { getImageProps } from "next/image";
import { useEffect, useState } from "react";
import { Escudo } from "@/components/escudo";
import { LoginForm } from "./login-form";

/**
 * Las tres pantallas de Figma en una sola ruta: la bienvenida, el escudo sobre el campus
 * y el login. La introducción se ve una vez por sesión del navegador; después, o con
 * "reducir movimiento" activado, se entra directo al formulario.
 */
type Phase = "pending" | "welcome" | "escudo" | "login";

const INTRO_SEEN_KEY = "reservas.intro-visto";
const WELCOME_MS = 2200;
const ESCUDO_MS = 1600;

export function LoginScreen() {
  const [phase, setPhase] = useState<Phase>("pending");

  useEffect(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const timeline: [Phase, number][] =
      introSeen() || reduceMotion
        ? [["login", 0]]
        : [
            ["welcome", 0],
            ["escudo", WELCOME_MS],
            ["login", WELCOME_MS + ESCUDO_MS],
          ];
    const timers = timeline.map(([next, delay]) => setTimeout(() => setPhase(next), delay));
    return () => timers.forEach(clearTimeout);
  }, []);

  useEffect(() => {
    if (phase === "login") markIntroSeen();
  }, [phase]);

  const inIntro = phase !== "login";

  return (
    <div
      className={`relative isolate flex flex-col overflow-hidden bg-brand-sky ${inIntro ? "h-dvh" : "min-h-dvh"}`}
    >
      <CampusBackground />
      <div
        aria-hidden
        className={`-z-10 absolute inset-0 bg-brand-night/75 transition-opacity duration-700 motion-reduce:transition-none ${phase === "login" ? "opacity-100" : "opacity-0"}`}
      />

      <Welcome phase={phase} />

      {/* Pantalla 2: el escudo grande sobre el campus. */}
      <div
        aria-hidden
        className={`pointer-events-none absolute inset-0 flex items-center justify-center pb-[12dvh] transition duration-700 ease-out motion-reduce:transition-none ${
          phase === "escudo"
            ? "scale-100 opacity-100"
            : phase === "login"
              ? "-translate-y-1/4 scale-50 opacity-0"
              : "scale-90 opacity-0"
        }`}
      >
        <Escudo surface="dark" className="w-[min(56vw,18rem)] drop-shadow-lg" />
      </div>

      <Login visible={phase === "login"} />

      {inIntro && phase !== "pending" && (
        <button
          type="button"
          onClick={() => setPhase("login")}
          className="absolute right-4 top-4 z-30 rounded-full bg-black/20 px-4 py-1.5 text-sm font-medium text-white backdrop-blur-sm transition hover:bg-black/30"
        >
          Saltar
        </button>
      )}
    </div>
  );
}

/** Pantalla 1: el panel azul en diagonal sobre la foto, con el escudo y el saludo. */
function Welcome({ phase }: { phase: Phase }) {
  const shown = phase === "pending" || phase === "welcome";
  return (
    <div
      aria-hidden={!shown}
      className={`absolute inset-0 z-10 transition-transform duration-700 ease-in motion-reduce:transition-none ${shown ? "translate-y-0" : "translate-y-full"}`}
    >
      {/* Mientras se decide si se ve la introducción, todo es azul: así no parpadea la foto. */}
      <div
        className={`absolute inset-0 bg-brand-sky transition-opacity duration-500 ${phase === "pending" ? "opacity-100" : "opacity-0"}`}
      />
      <div className="absolute inset-0 bg-brand-sky [clip-path:polygon(0_27%,100%_37%,100%_100%,0_100%)]" />
      <div className="absolute inset-0 bg-brand-blue [clip-path:polygon(0_72%,100%_68%,100%_100%,0_100%)]" />

      <div
        className={`relative flex h-full flex-col items-center px-6 text-center text-white transition-opacity duration-500 ${phase === "welcome" ? "opacity-100" : "opacity-0"}`}
      >
        <Escudo surface="dark" className="mt-[19dvh] w-[min(40vw,15rem)] drop-shadow-md" />
        <p className="mt-[3dvh] font-display text-xl font-bold tracking-wide sm:text-2xl">
          BIENVENIDO A
        </p>
        <p className="mt-3 font-display text-2xl font-light sm:text-3xl">UNIVERSIDAD DEL</p>
        <p className="font-display text-4xl font-bold sm:text-5xl">MAGDALENA</p>
        <Image
          src="/images/lema-incluyente.webp"
          alt="Una universidad más incluyente e innovadora"
          width={304}
          height={58}
          style={{ height: "auto" }}
          className="mt-auto mb-[10dvh] w-[min(78vw,20rem)]"
        />
      </div>
    </div>
  );
}

/**
 * Pantalla 3. En el celular, el saludo arriba y el formulario en una hoja que sube;
 * desde lg, el saludo a la izquierda sobre la foto y el formulario en un panel a la derecha.
 */
function Login({ visible }: { visible: boolean }) {
  return (
    <div
      aria-hidden={!visible}
      inert={!visible}
      className="flex flex-1 flex-col lg:grid lg:grid-cols-[1fr_minmax(28rem,36rem)]"
    >
      <header
        className={`flex flex-col px-6 pb-10 pt-12 text-white transition duration-700 motion-reduce:transition-none sm:px-10 lg:justify-center lg:px-16 lg:pb-12 xl:px-24 ${visible ? "translate-y-0 opacity-100 delay-200" : "translate-y-4 opacity-0"}`}
      >
        <Escudo surface="dark" className="w-20 drop-shadow-md sm:w-24 lg:w-36" />
        <h1 className="mt-5 text-3xl font-bold sm:text-4xl lg:mt-8 lg:text-6xl">¡Hola!</h1>
        <p className="mt-2 text-base font-light text-white/85 sm:text-lg lg:mt-4 lg:max-w-md lg:text-2xl">
          Inicia sesión con tu correo institucional
        </p>
      </header>

      <main
        className={`flex flex-1 flex-col rounded-t-[1.75rem] bg-white px-6 pb-8 pt-10 shadow-2xl transition-transform duration-700 ease-out motion-reduce:transition-none sm:px-10 lg:rounded-none lg:rounded-l-[2rem] lg:justify-center lg:px-14 ${
          visible ? "translate-y-0 lg:translate-x-0" : "translate-y-full lg:translate-y-0 lg:translate-x-full"
        }`}
      >
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col lg:flex-none">
          <h2 className="mb-7 text-3xl font-bold text-foreground">Iniciar sesión</h2>
          <LoginForm />
        </div>
      </main>
    </div>
  );
}

/**
 * El campus de fondo: la foto vertical en el celular y la horizontal desde lg,
 * con <picture> para que el navegador descargue solo la que va a mostrar.
 */
function CampusBackground() {
  const common = { alt: "", sizes: "100vw", quality: 80 };
  const {
    props: { srcSet: desktop },
  } = getImageProps({ ...common, src: "/images/campus-escritorio.webp", width: 1280, height: 854 });
  const {
    props: { srcSet: mobile, ...rest },
  } = getImageProps({ ...common, src: "/images/campus-movil.webp", width: 1080, height: 2341 });

  return (
    <picture>
      <source media="(min-width: 64rem)" srcSet={desktop} />
      <source srcSet={mobile} />
      <img
        {...rest}
        alt=""
        fetchPriority="high"
        className="absolute inset-0 -z-20 size-full object-cover object-[center_70%] lg:object-center"
      />
    </picture>
  );
}

function introSeen(): boolean {
  try {
    return sessionStorage.getItem(INTRO_SEEN_KEY) === "1";
  } catch {
    return false;
  }
}

function markIntroSeen() {
  try {
    sessionStorage.setItem(INTRO_SEEN_KEY, "1");
  } catch {
    // Sin almacenamiento (modo privado estricto): la introducción se repetirá, nada más.
  }
}
