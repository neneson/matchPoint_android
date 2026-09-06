import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";

import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/_authenticated/preparar")({
  component: PrepararPage,
  head: () => ({
    meta: [
      { title: "Preparar match | Marcador de Tenis" },
      { name: "description", content: "Ingresa el rival, haz el sorteo y comienza el match." },
      { property: "og:title", content: "Preparar match | Marcador de Tenis" },
      { property: "og:description", content: "Ingresa el rival, haz el sorteo y comienza el match." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});

type Ganador = "local" | "visitante";
type Eleccion = "saque" | "lado";

const inputClass =
  "w-full rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:ring-2 focus:ring-ring";

function PrepararPage() {
  const navigate = useNavigate();
  const [nombreLocal, setNombreLocal] = useState("Local");
  const [rival, setRival] = useState("");
  const [ganador, setGanador] = useState<Ganador | null>(null);
  const [eleccion, setEleccion] = useState<Eleccion | null>(null);
  const [sorteando, setSorteando] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      const { data: userData } = await supabase.auth.getUser();
      const user = userData.user;
      if (!user) return;
      const { data } = await supabase.from("profiles").select("nombre").eq("id", user.id).maybeSingle();
      if (!active) return;
      const nombre = data?.nombre?.trim();
      setNombreLocal(nombre && nombre.length > 0 ? nombre : (user.email ?? "Local").split("@")[0]!);
    })();
    return () => {
      active = false;
    };
  }, []);

  const sortear = () => {
    setSorteando(true);
    setEleccion(null);
    window.setTimeout(() => {
      setGanador(Math.random() < 0.5 ? "local" : "visitante");
      setSorteando(false);
    }, 500);
  };

  const nombreGanador = ganador === "local" ? nombreLocal : rival.trim() || "Visitante";

  // Quién saca: si el ganador elige saque, saca él; si elige lado, saca el otro.
  const sacaLocal =
    ganador === null || eleccion === null
      ? true
      : eleccion === "saque"
        ? ganador === "local"
        : ganador !== "local";

  const puedeComenzar = rival.trim().length > 0 && ganador !== null && eleccion !== null;

  const comenzar = () => {
    if (!puedeComenzar) return;
    navigate({
      to: "/partido",
      search: {
        local: nombreLocal,
        visitante: rival.trim(),
        saca: sacaLocal ? "local" : "visitante",
      },
    });
  };

  const cerrarSesion = async () => {
    await supabase.auth.signOut();
    navigate({ to: "/auth", replace: true });
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-black p-4">
      <div className="w-full max-w-sm rounded-3xl border border-border bg-background p-6 shadow-2xl">
        <h1 className="text-center text-xl font-bold text-foreground">Preparar match</h1>
        <p className="mt-1 text-center text-sm text-muted-foreground">Local: {nombreLocal}</p>

        <div className="mt-6 space-y-1">
          <label className="text-xs text-muted-foreground" htmlFor="rival">Nombre del rival (visitante)</label>
          <input id="rival" value={rival} onChange={(e) => setRival(e.target.value)}
            className={inputClass} placeholder="Ej: Rene" />
        </div>

        <button type="button" onClick={sortear} disabled={rival.trim().length === 0 || sorteando}
          className="mt-5 w-full rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
          {sorteando ? "Sorteando..." : "¿Quién comienza? (sorteo)"}
        </button>

        {ganador && !sorteando && (
          <div className="mt-5 rounded-xl bg-card p-4">
            <p className="text-center text-sm font-semibold text-card-foreground">
              Gana el sorteo: {nombreGanador}
            </p>
            <p className="mt-1 text-center text-xs text-muted-foreground">Elige saque o lado de la cancha</p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setEleccion("saque")}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${eleccion === "saque" ? "bg-set-active text-primary-foreground" : "bg-muted text-muted-foreground"}`}>
                Sacar
              </button>
              <button type="button" onClick={() => setEleccion("lado")}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${eleccion === "lado" ? "bg-set-active text-primary-foreground" : "bg-muted text-muted-foreground"}`}>
                Elegir lado
              </button>
            </div>
            {eleccion && (
              <p className="mt-3 text-center text-xs text-muted-foreground">
                Saca: {sacaLocal ? nombreLocal : rival.trim() || "Visitante"}
              </p>
            )}
          </div>
        )}

        <button type="button" onClick={comenzar} disabled={!puedeComenzar}
          className="mt-6 w-full rounded-lg bg-accent px-4 py-2 text-sm font-bold text-accent-foreground disabled:opacity-50">
          Comenzar match
        </button>

        <button type="button" onClick={cerrarSesion}
          className="mt-4 w-full text-center text-xs text-muted-foreground underline">
          Cerrar sesión
        </button>
      </div>
    </main>
  );
}
