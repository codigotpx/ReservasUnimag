import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { ClientOnly } from "@/components/client-only";
import { PendingCheckOuts } from "@/features/checkout/components/pending-check-outs";

export const metadata: Metadata = {
  title: "Pendientes de check-out · Reservas Unimagdalena",
};

export default function PendingReviewsPage() {
  return (
    <>
      <AdminHeader title="Pendientes de check-out" />
      {/* Los avisos de "menos de 48 h" se calculan con la hora actual. */}
      <ClientOnly>
        <PendingCheckOuts />
      </ClientOnly>
    </>
  );
}
