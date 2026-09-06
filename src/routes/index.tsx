import { createFileRoute, redirect } from "@tanstack/react-router";

import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/")({
  ssr: false,
  beforeLoad: async () => {
    const { data } = await supabase.auth.getUser();
    if (data.user) throw redirect({ to: "/preparar" });
    throw redirect({ to: "/auth" });
  },
  component: () => null,
  head: () => ({
    meta: [
      { title: "Marcador de Tenis para Smartwatch" },
      { name: "description", content: "Lleva el marcador de tus partidos de tenis desde tu reloj inteligente." },
      { property: "og:title", content: "Marcador de Tenis para Smartwatch" },
      { property: "og:description", content: "Lleva el marcador de tus partidos de tenis desde tu reloj inteligente." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});
