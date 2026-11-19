import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { MyReservationsScreen } from "@/features/reservations/components/my-reservations-screen";

export const metadata: Metadata = {
  title: "Mis reservas | Reservas Unimagdalena",
};

export default function MyReservationsPage() {
  return (
    <>
      <PageHeader title="Mis reservas" />
      {/* La pestaña vive en la URL (useSearchParams), que solo se conoce en el navegador. */}
      <Suspense>
        <MyReservationsScreen />
      </Suspense>
    </>
  );
}
