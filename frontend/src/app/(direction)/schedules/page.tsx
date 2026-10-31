import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { ClientOnly } from "@/components/client-only";
import { ImportScheduleScreen } from "@/features/schedules/components/import-schedule-screen";

export const metadata: Metadata = {
  title: "Importar horarios · Reservas Unimagdalena",
};

export default function SchedulesPage() {
  return (
    <>
      <AdminHeader title="Importar horarios semestrales" />
      {/* Los semestres que se ofrecen salen de la fecha de hoy. */}
      <ClientOnly>
        <ImportScheduleScreen />
      </ClientOnly>
    </>
  );
}
