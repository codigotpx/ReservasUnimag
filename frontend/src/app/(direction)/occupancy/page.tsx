import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { ClientOnly } from "@/components/client-only";
import { OccupancyCalendar } from "@/features/occupancy/components/occupancy-calendar";

export const metadata: Metadata = {
  title: "Ocupación de espacios | Reservas Unimagdalena",
};

export default function OccupancyPage() {
  return (
    <>
      <AdminHeader title="Ocupación de espacios" />
      {/* La semana que se abre es la de hoy, que solo se conoce en el navegador. */}
      <ClientOnly>
        <OccupancyCalendar />
      </ClientOnly>
    </>
  );
}
