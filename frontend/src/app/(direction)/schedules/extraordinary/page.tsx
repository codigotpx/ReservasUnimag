import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { ClientOnly } from "@/components/client-only";
import { ExtraordinaryNeedForm } from "@/features/schedules/components/extraordinary-need-form";

export const metadata: Metadata = {
  title: "Actividades extraordinarias · Reservas Unimagdalena",
};

export default function ExtraordinaryNeedPage() {
  return (
    <>
      <AdminHeader title="Actividades extraordinarias" />
      {/* La fecha mínima es hoy, que solo se conoce en el navegador. */}
      <ClientOnly>
        <ExtraordinaryNeedForm />
      </ClientOnly>
    </>
  );
}
