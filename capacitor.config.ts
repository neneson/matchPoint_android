import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Configuración de Capacitor para empaquetar el marcador como app nativa
 * (Android + iOS). Este archivo es inerte hasta instalar las dependencias de
 * Capacitor y correr `npx cap add`. Ver `MIGRACION-SUPABASE-CAPACITOR.md`.
 *
 * `webDir` → `.output/public`:
 *   SPA mode está activado en `vite.config.ts` (`tanstackStart.spa`). `npm run
 *   build` prerenderiza el shell y lo escribe como `.output/public/index.html`
 *   junto a `assets/`. Esa carpeta es el build estático que empaqueta Capacitor.
 */
const config: CapacitorConfig = {
  appId: "cl.matchpoint.marcador",
  appName: "Matchpoint",
  webDir: ".output/public",
  // El WebView sirve la app desde https://localhost (Android) / capacitor://localhost
  // (iOS). El cliente de Supabase habla por HTTPS al proyecto remoto; hay que
  // añadir estos orígenes en Supabase → Auth → URL Configuration (ver guía).
  server: {
    androidScheme: "https",
  },
};

export default config;
