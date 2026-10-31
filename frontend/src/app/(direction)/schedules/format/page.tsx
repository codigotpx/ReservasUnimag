import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { FormatGuide } from "@/features/schedules/components/format-guide";

export const metadata: Metadata = {
  title: "Formato del archivo de horarios · Reservas Unimagdalena",
};

export default function FormatGuidePage() {
  return (
    <>
      <AdminHeader title="Formato del archivo de horarios" />
      <FormatGuide />
    </>
  );
}
