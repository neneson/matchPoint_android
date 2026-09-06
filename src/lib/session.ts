import type { UsuarioPublico } from "./local-auth";

/**
 * Sesión local del lado del cliente. Guarda en localStorage al usuario que
 * validó el login/registro contra el JSON local. No lleva token: solo marca
 * qué cuenta local está activa para los guards de las rutas.
 */

const CLAVE_SESION = "mp_sesion";

export function guardarSesion(user: UsuarioPublico): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(CLAVE_SESION, JSON.stringify(user));
  } catch {
    // localStorage no disponible: se ignora
  }
}

export function obtenerSesion(): UsuarioPublico | null {
  if (typeof window === "undefined") return null;
  try {
    const crudo = window.localStorage.getItem(CLAVE_SESION);
    if (!crudo) return null;
    const user = JSON.parse(crudo) as UsuarioPublico;
    return user && typeof user.email === "string" ? user : null;
  } catch {
    return null;
  }
}

export function limpiarSesion(): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(CLAVE_SESION);
  } catch {
    // se ignora
  }
}
