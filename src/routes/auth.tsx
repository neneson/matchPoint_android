import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/auth")({
  ssr: false,
  component: AuthPage,
  head: () => ({
    meta: [
      { title: "Ingresar | Marcador de Tenis" },
      { name: "description", content: "Ingresa o regístrate para llevar el marcador de tus partidos de tenis." },
      { property: "og:title", content: "Ingresar | Marcador de Tenis" },
      { property: "og:description", content: "Ingresa o regístrate para llevar el marcador de tus partidos de tenis." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});

const inputClass =
  "w-full rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:ring-2 focus:ring-ring";

function AuthPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [nombre, setNombre] = useState("");
  const [rut, setRut] = useState("");

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setLoading(true);
    const { error: signInError } = await supabase.auth.signInWithPassword({ email, password });
    setLoading(false);
    if (signInError) {
      setError("No pudimos ingresar. Revisa tu email y contraseña.");
      return;
    }
    if (typeof window !== "undefined") {
      window.localStorage.setItem("recordarme", remember ? "1" : "0");
    }
    navigate({ to: "/preparar" });
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setLoading(true);
    const { data, error: signUpError } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: `${window.location.origin}/preparar`,
        data: { nombre, rut },
      },
    });
    setLoading(false);
    if (signUpError) {
      setError(signUpError.message.includes("already")
        ? "Ese email ya está registrado. Prueba ingresando."
        : "No pudimos crear la cuenta. Revisa los datos.");
      return;
    }
    if (data.session) {
      navigate({ to: "/preparar" });
      return;
    }
    setMessage("Cuenta creada. Revisa tu correo para confirmarla y luego ingresa.");
    setMode("login");
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-black p-4">
      <div className="w-full max-w-sm rounded-3xl border border-border bg-background p-6 shadow-2xl">
        <h1 className="text-center text-xl font-bold text-foreground">
          {mode === "login" ? "Ingresar" : "Registrarse"}
        </h1>

        {mode === "login" ? (
          <form onSubmit={handleLogin} className="mt-6 space-y-4">
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="email">Email</label>
              <input id="email" type="email" required value={email}
                onChange={(e) => setEmail(e.target.value)} className={inputClass} placeholder="tu@email.com" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="password">Contraseña</label>
              <input id="password" type="password" required value={password}
                onChange={(e) => setPassword(e.target.value)} className={inputClass} placeholder="••••••••" />
            </div>
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              Recordarme
            </label>
            <button type="submit" disabled={loading}
              className="w-full rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60">
              {loading ? "Ingresando..." : "Ingresar"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="mt-6 space-y-4">
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="nombre">Nombre</label>
              <input id="nombre" required value={nombre} onChange={(e) => setNombre(e.target.value)}
                className={inputClass} placeholder="Tu nombre" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="rut">Rut</label>
              <input id="rut" required value={rut} onChange={(e) => setRut(e.target.value)}
                className={inputClass} placeholder="12.345.678-9" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="remail">Email</label>
              <input id="remail" type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                className={inputClass} placeholder="tu@email.com" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="rpassword">Contraseña</label>
              <input id="rpassword" type="password" required minLength={6} value={password}
                onChange={(e) => setPassword(e.target.value)} className={inputClass} placeholder="••••••••" />
            </div>
            <button type="submit" disabled={loading}
              className="w-full rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60">
              {loading ? "Creando cuenta..." : "Registrarse"}
            </button>
          </form>
        )}

        {error && <p className="mt-4 text-center text-sm text-destructive">{error}</p>}
        {message && <p className="mt-4 text-center text-sm text-accent">{message}</p>}

        <button
          type="button"
          onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(null); setMessage(null); }}
          className="mt-6 w-full text-center text-sm text-muted-foreground underline"
        >
          {mode === "login" ? "¿No tienes cuenta? Registrarse" : "Ya tengo cuenta, ingresar"}
        </button>
      </div>
    </main>
  );
}
