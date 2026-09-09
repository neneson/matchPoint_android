import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { haySesion } from "@/lib/auth";

export const Route = createFileRoute("/_authenticated")({
  ssr: false,
  beforeLoad: async () => {
    if (!(await haySesion())) throw redirect({ to: "/auth" });
  },
  component: () => <Outlet />,
});
