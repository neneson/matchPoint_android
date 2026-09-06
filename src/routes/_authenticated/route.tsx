import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { obtenerSesion } from "@/lib/session";

export const Route = createFileRoute("/_authenticated")({
  ssr: false,
  beforeLoad: () => {
    const user = obtenerSesion();
    if (!user) throw redirect({ to: "/auth" });
    return { user };
  },
  component: () => <Outlet />,
});
