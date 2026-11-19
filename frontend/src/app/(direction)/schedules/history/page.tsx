import type { Metadata } from "next";
import { AdminHeader } from "@/components/admin-shell";
import { UploadHistory } from "@/features/schedules/components/upload-history";

export const metadata: Metadata = {
  title: "Horarios importados | Reservas Unimagdalena",
};

export default function UploadHistoryPage() {
  return (
    <>
      <AdminHeader title="Horarios importados" />
      <UploadHistory />
    </>
  );
}
