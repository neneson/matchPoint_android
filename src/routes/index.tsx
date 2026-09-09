import { createFileRoute, redirect } from "@tanstack/react-router";

import { haySesion } from "@/lib/auth";

export const Route = createFileRoute("/")({
  ssr: false,
  beforeLoad: async () => {
    throw redirect({ to: (await haySesion()) ? "/preparar" : "/auth" });
  },
  component: () => null,
  head: () => ({
    meta: [
      { title: "Marcador de Tenis para Smartwatch" },
      {
        name: "description",
        content: "Lleva el marcador de tus partidos de tenis desde tu reloj inteligente.",
      },
      { property: "og:title", content: "Marcador de Tenis para Smartwatch" },
      {
        property: "og:description",
        content: "Lleva el marcador de tus partidos de tenis desde tu reloj inteligente.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});
