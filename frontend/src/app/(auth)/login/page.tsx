import type { Metadata } from "next";
import { LoginScreen } from "@/features/auth/components/login-screen";

export const metadata: Metadata = {
  title: "Iniciar sesión | Reservas Unimagdalena",
};

export default function LoginPage() {
  return <LoginScreen />;
}
