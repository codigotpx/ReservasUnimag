"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent, type ReactNode } from "react";
import { Lock, Mail } from "lucide-react";
import { ApiRequestError } from "@/lib/api/client";
import { signIn } from "../api";

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await signIn(email.trim(), password);
      router.replace("/resources");
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? failure.problem.detail
          : "No se pudo iniciar sesión. Intenta de nuevo.",
      );
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <Field label="Correo institucional" htmlFor="email" icon={<Mail className="size-5" />}>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          inputMode="email"
          required
          placeholder="correo@unimagdalena.edu.co"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={inputClass}
        />
      </Field>

      <Field label="Contraseña" htmlFor="password" icon={<Lock className="size-5" />}>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          placeholder="Contraseña"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={inputClass}
        />
      </Field>

      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-sm">
        <label className="flex cursor-pointer items-center gap-2.5 text-gray-500">
          <input
            type="checkbox"
            name="remember"
            defaultChecked
            className="size-5 rounded border-gray-300 accent-brand-navy"
          />
          Recordarme
        </label>
        <Link href="/forgot-password" className="font-medium text-brand-navy hover:underline">
          ¿Olvidaste tu contraseña?
        </Link>
      </div>

      {error && (
        <p role="alert" className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="mt-1 h-12 rounded-full bg-brand-navy text-base font-semibold text-white transition hover:bg-brand-navy/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-navy disabled:cursor-wait disabled:opacity-70"
      >
        {submitting ? "Iniciando sesión…" : "Iniciar sesión"}
      </button>

      <p className="text-center text-sm text-gray-500">
        ¿No tienes cuenta?{" "}
        <Link href="/register" className="font-bold text-brand-navy hover:underline">
          Regístrate
        </Link>
      </p>
    </form>
  );
}

const inputClass =
  "h-12 w-full rounded-full border border-gray-200 bg-white pl-12 pr-5 text-base text-foreground placeholder:text-gray-400 outline-none transition focus:border-brand-navy focus:ring-2 focus:ring-brand-navy/20";

function Field({
  label,
  htmlFor,
  icon,
  children,
}: {
  label: string;
  htmlFor: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={htmlFor} className="text-sm font-medium text-foreground">
        {label}
      </label>
      <div className="relative">
        <span className="pointer-events-none absolute inset-y-0 left-4 flex items-center text-gray-500">
          {icon}
        </span>
        {children}
      </div>
    </div>
  );
}
