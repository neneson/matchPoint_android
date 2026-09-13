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

**Fase 4 — Sesión larga. ✅ HECHA (2026-09-10, ver `wear/docs/FASE-4.md`).**
`AmbientLifecycleObserver` deja la actividad always-on y `AmbientScoreboard` la pinta en negro
OLED, sin rellenos, con anti-quemado y **sin tick de 1 s** (en ambient el sistema despierta la
app una vez por minuto). Eso resuelve el apagado a los ~15 s mejor que `KeepScreenOn()`.
`MatchService` publica el marcador como **Ongoing Activity** desde un servicio en primer plano
`specialUse` (targetSdk 35 exige tipo; `health` pediría permisos de sensores) y **lee el mismo
DataStore que escribe el ViewModel**, sin binding ni segunda fuente de verdad; se detiene solo
al terminar el partido. El partido se persiste como un JSON en una clave de DataStore, con
`startedAt`/`stoppedAt` para que **el cronómetro también sobreviva**; un JSON de otra versión se
descarta en vez de reventar. Dos tropiezos: `POST_NOTIFICATIONS` es permiso de ejecución en
Wear 4+, y el servicio se mataba solo al arrancar porque el flujo emite `null` antes del primer
guardado. **28 tests JVM verdes.** Falta medir consumo real en un reloj físico.

**Fase 5 — Auth y teléfono. ✅ HECHA con la opción (1) (2026-09-10, ver `wear/docs/FASE-5.md`).**
`PrepararScreen`: app *standalone* **sin login**, dos nombres por dictado
(`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` — en Wear lo atiende el panel del sistema, con voz,
teclado y escritura a mano; sin dependencias extra), sorteo del saque y "Empezar". Los nombres
se recuerdan entre partidos. El arranque decide pantalla según el estado guardado (nuevo
`MatchState.empezado`): partido a medias → marcador retomado, si no → preparar. Sin
`SwipeDismissableNavHost` a propósito (el gesto de volver atrás sacaría del marcador en pleno
partido); se quitó `compose-navigation`, que estaba sin uso. **32 tests JVM verdes.**
**(2) Data Layer queda pendiente** y no es un detalle: obliga a meter un `WearableListenerService`
en el `android/` de Capacitor, que **lo regenera `npx cap sync`** — habría que sacarlo a un plugin
o a un árbol versionado aparte. **(3) descartada**, como decía el plan.

**Fase 6 — Batería. ✅ HECHA (2026-09-13, ver `wear/docs/FASE-6.md`).** El always-on pasa a
ser opcional y se cae solo en ahorro de energía; el cronómetro no cuenta si nadie lo mira;
la notificación es local, silenciosa y sólo se actualiza con la app en segundo plano;
paleta OLED-negra; R8 (23,3 MB → 2,5 MB). 39 tests JVM, 7 de ellos de batería.

**Fase 7 — Empaquetado (0.5 día).** Firmar con la keystore ya creada (`android/matchpoint-release.jks`),
probar en reloj real (`adb connect <ip>:5555` por Wi-Fi). Para Play: subir como AAB con el
form factor Wear OS declarado; requiere ficha, capturas de reloj y pasar la revisión de calidad Wear.

## Riesgos

- **No hay reloj Wear OS físico verificado** en el entorno; el emulador no valida ambient
  ni consumo real de batería.
- ~~La **paleta actual es oscura pero no OLED-negra**~~: resuelto en la Fase 6 (fondo y
  botones casi negros, ambient `#000000`).
- Play exige que la app Wear cumpla las guidelines; una v1 por **sideload** evita ese bloqueo.
- No commitear: el repo está sincronizado con Lovable (ver `AGENTS.md`), un módulo `wear/`
  en la rama conectada puede confundir al editor. Trabajar en rama aparte.

## Total estimado

**7-11 días** de trabajo hasta un APK Wear funcional y probado; +2-3 días para publicar en Play.
