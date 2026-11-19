import type { ReactNode } from "react";
import { AdminShell } from "@/components/admin-shell";
import { RoleGate } from "@/features/auth/role-gate";
import { SessionProvider } from "@/features/auth/session";

export default function DirectionLayout({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <RoleGate allow={["DIRECCION_PROGRAMA"]}>
        <AdminShell>{children}</AdminShell>
      </RoleGate>
    </SessionProvider>
  );
}
