# Plan de migración a Wear OS

Fecha: 2026-09-09 · Estado actual: SPA (TanStack Start + React 19 + Tailwind) empaquetada
con Capacitor 6 → APK de teléfono (`cl.matchpoint.marcador`, minSdk 22 / targetSdk 34).

## Decisión

**Reescribir la UI del reloj en Kotlin + Compose for Wear OS**, reutilizando la lógica de
tenis (que ya es pura). El WebView de Capacitor arranca en un reloj y sirve para una demo
por sideload, pero no cumple las Wear OS app quality guidelines (sin rotary, sin ambient,
sin TimeText, sin soporte de pantalla redonda) → no es publicable en Play.

| Ruta | Esfuerzo | Sirve para |
|---|---|---|
| A. Capacitor tal cual en el reloj | horas | prototipo/sideload, validar tamaños |
| **B. Compose for Wear OS (elegida)** | ~1-2 semanas | app real, publicable |

## Qué se reutiliza y qué se rehace

| Pieza | Destino |
|---|---|
| `matchReducer` + `scorePoint`/`winGame` + constantes (`partido.tsx:24-193`) | **Port directo a Kotlin** (~150 líneas, sin dependencias de React) |
| Layout 2×2 + grilla 9 columnas, paleta (`styles.css:44-73`, oklch) | Rehacer en Compose; convertir tokens a `Color(0xFF…)` |
| Pantalla `preparar` (rival + sorteo + saque/lado) | Rehacer, simplificada al reloj |
| `auth` + Supabase (`lib/auth.ts`, `integrations/supabase/`) | **Fuera del reloj** en v1 (ver Fase 4) |
| App web/teléfono actual | Se mantiene tal cual, sin tocar |

## Fases

**Fase 0 — Prototipo. ✅ HECHA (2026-09-10, ver `wear/docs/FASE-0-1.md`).** AVD `matchpoint_wear`
(454×454 redonda, `android-34;android-wear;x86_64`). La premisa se confirma y peor de lo previsto:
la imagen Wear **no trae WebView**, así que el APK de Capacitor ni siquiera arranca; y el layout
de 320 px no "pierde las esquinas" sino que **no cabe** — el reloj mide 227×227 dp, con 160 dp
de cuadrado seguro. El apagado a ~15 s no se pudo medir (el AVD tiene la pantalla siempre encendida).

**Fase 1 — Módulo `wear/`. ✅ HECHA (2026-09-10, ver `wear/docs/FASE-0-1.md`).** Proyecto Gradle
independiente en `wear/` (no dentro de `android/`, que lo regenera `cap sync`): AGP 8.7.3 / Gradle 8.9 /
Kotlin 2.1.0 sobre JDK 17, `cl.matchpoint.marcador.wear`, minSdk 30 / targetSdk 35 / compileSdk 35,
`compose-material3:1.5.6`, manifest watch-only + `standalone=true`, release firmado con la keystore del
teléfono. `assembleDebug` y `assembleRelease` OK; instalado y corriendo en el AVD sin crashes.
Pendiente: activar R8 (21 MB de release sin minify).

**Fase 2 — Lógica. ✅ HECHA (2026-09-10, ver `wear/docs/FASE-2.md`).** `domain/MatchState.kt`
(port 1:1, Kotlin puro) + `domain/MatchViewModel.kt` (`StateFlow` + cronómetro que se congela al
terminar). **25 tests JVM verdes**: deuce, tie break 7-6 y la diferencia de 2, cierre de set,
fin de partido a 2 de 3, undo/reset/saque y `formatElapsed`. Probado además en el emulador
(el reducer responde al toque dentro del reloj). Tres divergencias documentadas: el reducer
ignora puntos con el partido terminado, las etiquetas de tie break se generan, y el **super
tie break** pasa a ser `Rules.superTiebreak` apagado por defecto (en la web la constante
estaba declarada pero nunca se usaba).

**Fase 3 — UI del reloj. ✅ HECHA (2026-09-10, ver `wear/docs/FASE-3.md`).**
`presentation/ScoreboardScreen.kt`, todo en dp con pesos, sin nada heredado de los 320 px.
`AppScaffold` pone el `TimeText` del sistema y la fila 0 queda sólo con el cronómetro
(no se usa `ScreenScaffold`: es para pantallas con scroll). Toque = punto, long-press =
deshacer, corona = sumar/quitar al que saca, háptica en ambos, `AlertDialog` de Wear al
terminar con un `EdgeButton` "Nuevo" (si no, la app quedaba muerta: el reloj no tiene el
enlace *Nuevo match* de la web). Dos hallazgos: **el tablero y los botones necesitan
tratamientos opuestos** para el bisel — el tablero se insetea con `r - √(r² - y²)`, los
botones van casi de borde a borde y lo que sobresale se redondea con la curva del bisel
(insetarlos igual dejaba dos tiras de 104 dp); y **`verticalScrollPixels` viene invertido**
respecto de `AXIS_SCROLL`. Verificado con capturas en el emulador redondo; falta un reloj
cuadrado y uno físico.

**Fase 4 — Sesión larga (1-2 días).** Lo crítico para un partido de 2 h:
- **Ambient mode** (`AmbientLifecycleObserver`): versión de bajo consumo del marcador.
- **Ongoing Activity** + foreground service: el partido queda visible en el watch face y
  no se pierde el estado.
- Persistir el partido (`DataStore`) para sobrevivir a que se apague la pantalla o la app.

**Fase 5 — Auth y teléfono (1-2 días, opcional).** El login Supabase en el reloj es mal UX
(teclado de 1"). Opciones, en orden: (1) app *standalone* sin login, nombre del rival por
voz/dictado; (2) **Data Layer API** (`MessageClient`/`DataClient`) para recibir el perfil y
el token desde la app de teléfono ya existente; (3) Supabase directo en el reloj (último recurso).

**Fase 6 — Empaquetado (0.5 día).** Firmar con la keystore ya creada (`android/matchpoint-release.jks`),
probar en reloj real (`adb connect <ip>:5555` por Wi-Fi). Para Play: subir como AAB con el
form factor Wear OS declarado; requiere ficha, capturas de reloj y pasar la revisión de calidad Wear.

## Riesgos

- **No hay reloj Wear OS físico verificado** en el entorno; el emulador no valida ambient
  ni consumo real de batería.
- La **paleta actual es oscura pero no OLED-negra**: en ambient conviene fondo `#000000` puro.
- Play exige que la app Wear cumpla las guidelines; una v1 por **sideload** evita ese bloqueo.
- No commitear: el repo está sincronizado con Lovable (ver `AGENTS.md`), un módulo `wear/`
  en la rama conectada puede confundir al editor. Trabajar en rama aparte.

## Total estimado

**7-11 días** de trabajo hasta un APK Wear funcional y probado; +2-3 días para publicar en Play.
