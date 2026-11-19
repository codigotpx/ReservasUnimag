/** ProblemDetail (RFC 9457) tal como lo traduce GlobalErrorHandler en el backend. */
export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
  module?: "MODULO_1" | "MODULO_3";
}

/** Lo que lanza apiFetch con cualquier respuesta >= 400, o cuando el backend no responde. */
export class ApiRequestError extends Error {
  constructor(readonly problem: ApiError) {
    super(problem.detail);
    this.name = "ApiRequestError";
  }
}

const unreachable: ApiError = {
  type: "about:blank",
  title: "Sin conexión",
  status: 0,
  detail: "No se pudo conectar con el servidor. Intenta de nuevo en un momento.",
};

/**
 * fetch hacia /api del backend. Next.js reenvía /api/* a Spring (next.config.ts),
 * así que la cookie de sesión httpOnly viaja sola por ser el mismo origen.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      credentials: "same-origin",
      headers: { Accept: "application/json", ...init.headers },
    });
  } catch {
    throw new ApiRequestError(unreachable);
  }

  if (!response.ok) {
    throw new ApiRequestError(await problemFrom(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function problemFrom(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (body.detail) {
      return { ...unreachable, ...body, status: response.status } as ApiError;
    }
  } catch {
    // Sin cuerpo JSON: p. ej. el proxy de Next.js cuando el backend está apagado.
  }
  return {
    ...unreachable,
    title: response.statusText || unreachable.title,
    status: response.status,
    detail: "Algo salió mal en el servidor. Intenta de nuevo en un momento.",
  };
}
