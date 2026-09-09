# Migración de auth a Supabase + empaquetado con Capacitor

Fecha: 2026-09-08 · Rama: `devRene` · Objetivo: quitar las *server functions* del
login para poder empaquetar el cliente con Capacitor (Android + iOS).

---

## 1. Qué cambió en el código (ya hecho)

El login pasa de **archivos JSON en el servidor** (`src/lib/local-auth.ts`,
`createServerFn`) a **Supabase Auth 100 % en el cliente**.

| Archivo | Cambio |
|---|---|
| `src/lib/auth.ts` | **Nuevo.** `registrarUsuario`, `iniciarSesion`, `cerrarSesion`, `haySesion`, `obtenerUsuarioActual`. Usa `supabase.auth.*` + tabla `profiles`. Sin server functions. |
| `src/lib/session.ts` | Reescrito: solo re-exporta helpers de `auth.ts` (`obtenerSesion` = `obtenerUsuarioActual`, `limpiarSesion` = `cerrarSesion`). Ya no hay "sesión local" en localStorage propia; la maneja `supabase-js`. |
| `src/routes/auth.tsx` | Usa `@/lib/auth`. Firma nueva: `iniciarSesion({ email, password })` (antes `{ data: { … } }`). Si el registro deja sesión activa (sin confirmación de email), entra directo a `/preparar`. |
| `src/routes/index.tsx` | `beforeLoad` ahora `async` → `haySesion()`. |
| `src/routes/_authenticated/route.tsx` | `beforeLoad` ahora `async` → `haySesion()`. |
| `src/routes/_authenticated/preparar.tsx` | Lee el nombre con `obtenerUsuarioActual()` (async) y cierra sesión con `cerrarSesion()`. |
| `capacitor.config.ts` | **Nuevo.** Scaffold inerte hasta instalar Capacitor. |

`src/lib/local-auth.ts` **se deja en el repo pero ya no se importa** (código
muerto). Se puede borrar cuando la migración esté confirmada.

`tsc --noEmit` pasa. Los archivos nuevos/reescritos pasan ESLint; `auth.tsx` y
`preparar.tsx` conservan violaciones de `prettier` **preexistentes** (JSX
compacto del generador de Lovable), no introducidas aquí.

### Esquema en Supabase — ya existe

`supabase/migrations/20260906021148_*.sql` y `…021159_*.sql` ya crean:

- tabla `public.profiles` (`id` = `auth.users.id`, `nombre`, `rut`, timestamps),
- RLS: cada quien ve/edita **solo su** fila,
- trigger `on_auth_user_created` → `handle_new_user()` copia `nombre`/`rut` desde
  `raw_user_meta_data` (lo que se manda en `options.data` del `signUp`).

**No hay SQL nuevo que escribir.** Solo verificar que esas migraciones estén
aplicadas en el proyecto `ormyvcxdeirglddxhrzu` (paso 2).

---

## 2. Pasos en Supabase (consola / CLI)

1. **Verificar migraciones aplicadas.** En el dashboard → Table editor: debe
   existir `profiles` con RLS activo y las 3 policies. Si no:
   ```bash
   npx supabase link --project-ref ormyvcxdeirglddxhrzu
   npx supabase db push
   ```
2. **Confirmación de email (Auth → Providers → Email):**
   - **Recomendado para la app de reloj: desactivar "Confirm email".** Así el
     `signUp` deja sesión activa y el usuario entra al toque. El código ya
     soporta ambos casos.
   - Si se deja activada: el usuario recibe un correo, y el link de confirmación
     debe apuntar a un dominio válido (ver paso 3). El deep-link de vuelta a la
     app nativa es trabajo extra (esquema `cl.matchpoint.marcador://`).
3. **Auth → URL Configuration → Redirect URLs / Site URL.** Añadir los orígenes
   desde donde va a correr el cliente:
   - `http://localhost:3000` (dev)
   - la URL publicada en Lovable
   - `https://localhost` y `capacitor://localhost` (WebView de Capacitor)
4. **Migrar usuarios existentes de `.matchpoint-auth/`** (si hay alguno que
   conservar): son 0–2 de prueba. Lo más simple es re-registrarlos desde la app.
   No hay import automático (las contraseñas locales son `scrypt`, no las de
   Supabase).

---

## 3. Empaquetar con Capacitor

> Requiere Android Studio (Android) y Xcode en macOS (iOS).

### 3.1 Instalar dependencias (NO se tocó `package.json` para no romper el sync con Lovable)

```bash
npm i -D @capacitor/cli
npm i @capacitor/core @capacitor/android @capacitor/ios
```

### 3.2 Build estático del cliente  ⚠️ punto crítico

TanStack Start no es un SPA puro. Todas las rutas de esta app ya son
`ssr: false`, así que falta solo generar el bundle estático (index.html +
assets) en modo SPA/prerender:

- Revisar en la doc de TanStack Start / `@lovable.dev/vite-tanstack-config` cómo
  activar **SPA mode** (prerender del shell + hidratación en cliente).
- Correr `npm run build` y localizar la carpeta con el `index.html` del cliente.
- Poner esa ruta en `webDir` de `capacitor.config.ts` (ahora dice `"dist"`).

Si el SPA mode diera guerra, alternativa temporal: `server.url` en
`capacitor.config.ts` apuntando a la URL publicada en Lovable (la app nativa
sería un envoltorio del sitio hospedado, no offline).

### 3.3 Añadir plataformas y correr

```bash
npx cap init            # si pide datos: appId cl.matchpoint.marcador, appName Matchpoint
npx cap add android
npx cap add ios
npm run build && npx cap sync
npx cap open android    # / npx cap open ios
```

### 3.4 Persistencia de sesión en el WebView

`src/integrations/supabase/client.ts` usa `localStorage` fuera del preview de
Lovable → **funciona dentro de Capacitor** (el WebView conserva `localStorage`
entre aperturas). 

Endurecimiento opcional (iOS puede purgar `localStorage` bajo presión de
almacenamiento): usar `@capacitor/preferences` como `storage` del cliente de
Supabase. `client.ts` está marcado como autogenerado; si se quiere, crear un
cliente propio para `auth` con ese adaptador. No es bloqueante para la v1.

---

## 4. Verificación

- [ ] `npm run dev` → registrar, cerrar sesión, ingresar, refrescar (sesión persiste), `/preparar` toma el nombre.
- [ ] Guards: entrar a `/partido` sin sesión redirige a `/auth`.
- [ ] `npm run build` genera el estático y `webDir` apunta bien.
- [ ] `npx cap run android` → mismo flujo dentro de la app.
- [ ] iOS en simulador (requiere macOS).

## 5. Rollback

Todo el cambio de auth es en el working tree hasta que se commitee. Para volver:
`git checkout -- src/lib/session.ts src/routes/auth.tsx src/routes/index.tsx src/routes/_authenticated/route.tsx src/routes/_authenticated/preparar.tsx` y borrar `src/lib/auth.ts` y `capacitor.config.ts`. `local-auth.ts` sigue intacto.
