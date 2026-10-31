import type { Metadata } from "next";
import { PageHeader } from "@/components/page-header";
import { ProfileCard } from "@/features/auth/components/profile-card";

export const metadata: Metadata = {
  title: "Perfil | Reservas Unimagdalena",
};

export default function ProfilePage() {
  return (
    <>
      <PageHeader title="Perfil" />
      <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 lg:py-10">
        <ProfileCard />
      </div>
    </>
  );
}
