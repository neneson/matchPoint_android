import { supabase } from "@/integrations/supabase/client";

/**
 * Autenticación con Supabase Auth (email + contraseña).
 *
 * Todo corre en el cliente: no hay server functions, así el bundle se puede
 * empaquetar con Capacitor y hablar directo con Supabase.
 *
 * - La identidad (email + hash de contraseña) la maneja `auth.users`.
 * - `nombre` y `rut` van en `options.data` del `signUp` → el trigger
 *   `handle_new_user` (ver `supabase/migrations/`) los copia a `public.profiles`.
 * - La sesión (tokens JWT) la persiste `supabase-js` en el storage configurado
 *   en `src/integrations/supabase/client.ts` (localStorage fuera del preview de
 *   Lovable, que es lo que aplica dentro del WebView de Capacitor).
 */

export type UsuarioPublico = {
  nombre: string;
  rut: string;
  email: string;
};

type Resultado = { ok: true; user: UsuarioPublico } | { ok: false; error: string };

function normalizarEmail(email: string): string {
  return email.trim().toLowerCase();
}

// Traduce los mensajes de Supabase (en inglés) a algo mostrable en español.
function traducirError(mensaje: string | undefined): string {
  const m = (mensaje ?? "").toLowerCase();
  if (m.includes("already registered") || m.includes("already been registered")) {
    return "Ese email ya está registrado. Prueba ingresando.";
  }
  if (m.includes("invalid login credentials")) {
    return "Email o contraseña incorrectos.";
  }
  if (m.includes("email not confirmed")) {
    return "Debes confirmar tu email antes de ingresar.";
  }
  if (m.includes("password should be at least") || m.includes("password is too short")) {
    return "La contraseña debe tener al menos 6 caracteres.";
  }
  if (m.includes("unable to validate email address") || m.includes("invalid email")) {
    return "El email no tiene un formato válido.";
  }
  if (m.includes("network") || m.includes("failed to fetch")) {
    return "Sin conexión. Revisa tu internet e inténtalo de nuevo.";
  }
  return "No pudimos completar la operación. Inténtalo de nuevo.";
}

async function leerPerfil(id: string): Promise<{ nombre: string; rut: string } | null> {
  const { data } = await supabase.from("profiles").select("nombre, rut").eq("id", id).maybeSingle();
  return data ?? null;
}

export async function registrarUsuario(datos: {
  nombre: string;
  rut: string;
  email: string;
  password: string;
}): Promise<Resultado> {
  const nombre = datos.nombre?.trim() ?? "";
  const rut = datos.rut?.trim() ?? "";
  const email = normalizarEmail(datos.email ?? "");
  const password = datos.password ?? "";

  if (!nombre || !rut || !email || !password) {
    return { ok: false, error: "Completa todos los campos." };
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return { ok: false, error: "El email no tiene un formato válido." };
  }
  if (password.length < 6) {
    return { ok: false, error: "La contraseña debe tener al menos 6 caracteres." };
  }

  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: { data: { nombre, rut } },
  });
  if (error) {
    return { ok: false, error: traducirError(error.message) };
  }

  // Con la confirmación de email desactivada, `signUp` ya deja sesión activa.
  // Aseguramos la fila de `profiles` por si el trigger no estuviera instalado
  // (idempotente gracias a onConflict). Si hace falta confirmar, no hay sesión
  // todavía y el upsert se omite: lo creará el trigger al confirmar.
  const uid = data.user?.id;
  if (uid && data.session) {
    await supabase.from("profiles").upsert({ id: uid, nombre, rut }, { onConflict: "id" });
  }

  return { ok: true, user: { nombre, rut, email } };
}

export async function iniciarSesion(datos: {
  email: string;
  password: string;
}): Promise<Resultado> {
  const email = normalizarEmail(datos.email ?? "");
  const password = datos.password ?? "";

  if (!email || !password) {
    return { ok: false, error: "Ingresa tu email y contraseña." };
  }

  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error || !data.user) {
    return { ok: false, error: traducirError(error?.message ?? "invalid login credentials") };
  }

  const perfil = await leerPerfil(data.user.id);
  return {
    ok: true,
    user: {
      nombre: perfil?.nombre ?? "",
      rut: perfil?.rut ?? "",
      email: data.user.email ?? email,
    },
  };
}

export async function cerrarSesion(): Promise<void> {
  await supabase.auth.signOut();
}

/** Verifica que haya sesión válida. Rápido: no consulta la tabla `profiles`. */
export async function haySesion(): Promise<boolean> {
  const { data } = await supabase.auth.getSession();
  return Boolean(data.session);
}

/** Usuario actual (sesión + perfil) o `null` si no hay sesión. */
export async function obtenerUsuarioActual(): Promise<UsuarioPublico | null> {
  const { data } = await supabase.auth.getSession();
  const u = data.session?.user;
  if (!u) return null;
  const perfil = await leerPerfil(u.id);
  return {
    nombre: perfil?.nombre ?? "",
    rut: perfil?.rut ?? "",
    email: u.email ?? "",
  };
}
