import { apiFetch } from "@/lib/api/client";
import type {
  ConfirmationResponse,
  ExtraordinaryNeedRequest,
  ExtraordinaryNeedResponse,
  ScheduleUploadDetail,
  ScheduleUploadsResponse,
  ValidationResponse,
} from "./types";

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

/** GET /api/schedules (UC3 Contratos §4): el histórico de cargas, de 20 en 20. */
export function fetchUploads(page = 1): Promise<ScheduleUploadsResponse> {
  return apiFetch<ScheduleUploadsResponse>(`/schedules?page=${page}`);
}

/** GET /api/schedules/{uploadId}: una carga con su reporte, para volver a mirarlo sin repetir la carga. */
export function fetchUpload(uploadId: string): Promise<ScheduleUploadDetail> {
  return apiFetch<ScheduleUploadDetail>(`/schedules/${encodeURIComponent(uploadId)}`);
}

/**
 * POST /api/schedules/extraordinary-needs (UC3 Contratos §3). Con confirm en false solo dice qué pasaría; con true
 * crea el bloqueo y cancela lo que desplace.
 */
export function registerExtraordinaryNeed(request: ExtraordinaryNeedRequest): Promise<ExtraordinaryNeedResponse> {
  return apiFetch<ExtraordinaryNeedResponse>("/schedules/extraordinary-needs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}
