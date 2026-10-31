import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { ClientOnly } from "@/components/client-only";
import { UploadDetailRoute } from "@/features/schedules/components/upload-detail-route";

export const metadata: Metadata = {
  title: "Carga de horario · Reservas Unimagdalena",
};

export default function UploadDetailPage() {
  return (
    <>
      <AdminHeader title="Horarios importados" />
      {/* El id sale de la URL y la carga se pide desde el navegador, con la sesión. */}
      <ClientOnly>
        <UploadDetailRoute />
      </ClientOnly>
    </>
  );
}
