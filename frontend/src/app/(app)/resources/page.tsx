import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { ResourcesScreen } from "@/features/resources/components/resources-screen";

export const metadata: Metadata = {
  title: "Consultar recursos | Reservas Unimagdalena",
};

export default function ResourcesPage() {
  return (
    <>
      <PageHeader title="Consultar recursos" />
      {/* La búsqueda vive en la URL (useSearchParams), que solo se conoce en el navegador. */}
      <Suspense>
        <ResourcesScreen />
      </Suspense>
    </>
  );
}
