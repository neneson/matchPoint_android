# Auth local por archivos JSON

Fecha: 2026-09-05 · Rama: `devRene` · **Sin commit ni push** (solo working tree)

## Qué se pidió

Que el registro genere un JSON en una carpeta local oculta con los datos del
formulario, con la contraseña cifrada; y que el login valide email + contraseña
contra ese JSON (considerando que la contraseña está cifrada).

## Qué se hizo

### Nuevos archivos

| Archivo | Rol |
|---|---|
| `src/lib/local-auth.ts` | Server functions `registrarUsuario` e `iniciarSesion` (leen/escriben los JSON). |
| `src/lib/session.ts` | Sesión de cliente en `localStorage` (clave `mp_sesion`), sin token. |

### Almacenamiento

- Carpeta oculta: **`.matchpoint-auth/usuarios/`** (en la raíz del proyecto, `process.cwd()`).
- Un JSON por usuario, nombre = `sha256(email)` → `.json`.
- Contenido: `nombre`, `rut`, `email`, `password`, `creadoEn`.

### Cifrado de la contraseña

- Algoritmo **scrypt** con **salt aleatoria de 16 bytes** por usuario.
- Se guarda como `scrypt:<saltHex>:<hashHex>`. Nunca en texto plano.
- En el login se vuelve a derivar el hash con la salt guardada y se compara con
  `crypto.timingSafeEqual` (comparación en tiempo constante).

### Login

`iniciarSesion` lee `.matchpoint-auth/usuarios/<sha256(email)>.json` y responde:

- sin archivo → "No encontramos una cuenta con ese email."
- email o hash no coinciden → "Email o contraseña incorrectos."
- todo OK → `{ ok: true, user: { nombre, rut, email } }` y el cliente guarda la sesión.

### Archivos modificados

- `src/routes/auth.tsx` — usa las server functions en vez de `supabase.auth.*`.
- `src/routes/index.tsx`, `src/routes/_authenticated/route.tsx`,
  `src/routes/_authenticated/preparar.tsx` — guards y nombre del jugador ahora
  salen de `obtenerSesion()` (localStorage) en vez de Supabase.
- `.gitignore` — ignora `.matchpoint-auth/`.

Supabase queda instalado y en `src/integrations/supabase/*` / `src/start.ts`, pero
las pantallas de auth ya no lo usan.

## Verificación

Contra el dev server (`npm run dev`), llamando las server functions por HTTP:

| Caso | Resultado |
|---|---|
| Registro nuevo | `ok:true`, JSON creado con password `scrypt:...` |
| Registro con email repetido | rechazado |
| Login con contraseña correcta | `ok:true` |
| Login con contraseña incorrecta | rechazado |
| Login con email inexistente | rechazado |
