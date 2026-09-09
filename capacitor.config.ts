import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Configuración de Capacitor para empaquetar el marcador como app nativa
 * (Android + iOS). Este archivo es inerte hasta instalar las dependencias de
 * Capacitor y correr `npx cap add`. Ver `MIGRACION-SUPABASE-CAPACITOR.md`.
 *
 * IMPORTANTE — `webDir`:
 *   Debe apuntar a la carpeta con el build ESTÁTICO del cliente (index.html +
 *   assets). TanStack Start no es un SPA puro; hay que generar ese build en
 *   modo SPA/prerender y confirmar la ruta real de salida:
 *     npm run build   # y revisar qué carpeta contiene el index.html del cliente
 *   Ajustar `webDir` a esa carpeta (típico: "dist" o ".output/public").
 */
const config: CapacitorConfig = {
  appId: "cl.matchpoint.marcador",
  appName: "Matchpoint",
  webDir: "dist",
  // El WebView sirve la app desde https://localhost (Android) / capacitor://localhost
  // (iOS). El cliente de Supabase habla por HTTPS al proyecto remoto; hay que
  // añadir estos orígenes en Supabase → Auth → URL Configuration (ver guía).
  server: {
    androidScheme: "https",
  },
};

export default config;
