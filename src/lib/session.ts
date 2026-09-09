/**
 * Sesión = sesión de Supabase Auth, persistida por `supabase-js` en el storage
 * del cliente (localStorage dentro del WebView de Capacitor).
 *
 * Este módulo solo re-exporta los helpers de `auth.ts` que usan los guards de
 * rutas, para no tocar todos los imports. Ya no hay una "sesión local" propia
 * en localStorage: `guardarSesion` desapareció (Supabase la guarda solo).
 */

export type { UsuarioPublico } from "./auth";
export {
  obtenerUsuarioActual as obtenerSesion,
  cerrarSesion as limpiarSesion,
  haySesion,
} from "./auth";
