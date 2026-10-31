import { apiFetch } from "@/lib/api/client";
import type { ConfirmationResponse, ValidationResponse } from "./types";

/** POST /api/schedules/validations (UC3 Contratos §1): valida y guarda la carga, sin tocar ninguna reserva. */
export function validateSchedule(input: {
  file: File;
  academicTerm: string;
  termStart: string;
  termEnd: string;
}): Promise<ValidationResponse> {
  const body = new FormData();
  body.append("file", input.file);
  body.append("academicTerm", input.academicTerm);
  body.append("termStart", input.termStart);
  body.append("termEnd", input.termEnd);
  // Sin Content-Type: el navegador pone el multipart con su boundary.
  return apiFetch<ValidationResponse>("/schedules/validations", { method: "POST", body });
}

/** POST /api/schedules/{uploadId}/confirmation (UC3 Contratos §2): aplica exactamente lo que se validó. */
export function confirmSchedule(uploadId: string): Promise<ConfirmationResponse> {
  return apiFetch<ConfirmationResponse>(`/schedules/${encodeURIComponent(uploadId)}/confirmation`, {
    method: "POST",
  });
}
