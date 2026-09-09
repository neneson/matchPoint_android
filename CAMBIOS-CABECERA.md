# Cambio: cabecera del cuadrante superior (2026-09-08)

El cuadrante superior del marcador (`src/routes/_authenticated/partido.tsx`) pasa de
una matriz `[2][9]` a `[3][9]`.

## Estructura de la grilla

| Fila | Alto | Contenido |
|------|------|-----------|
| 0 | 40 px | 3 bloques de 3 columnas: **izq.** = tiempo transcurrido del partido · **centro** = vacío · **der.** = hora local 24 h |
| 1 | 60 px | jugador **local** (nombre, sets, track) |
| 2 | 60 px | jugador **visitante** (misma estructura) |

Suma 160 px, igual que antes → sigue calzando con la mitad inferior de puntos.
Clases: `grid-rows-[40px_60px_60px] gap-x-1` (sin `gap-y` ni `p-1` para respetar los altos exactos).

## Fila 0

- Fondo **azul marino**: token nuevo `--navy` en `src/styles.css` (`oklch(0.28 0.09 264)`), clase `bg-navy`.
- Texto **gris**: `text-muted-foreground`.
- Reloj: `useEffect` + `setInterval` de 1 s en el cliente. Helpers `formatElapsed`
  (`MM:SS`, o `H:MM:SS` pasada la hora) y `formatClock` (`HH:MM`, 24 h).
- `startedAt` / `now` arrancan en `null` para evitar desajuste de hidratación (la ruta es `ssr: false`, pero queda robusto igual).
- Padding `pl-7` / `pr-7` en los bloques laterales para que los botones flotantes S y R no tapen la hora.
- **El tiempo transcurrido se detiene al terminar el partido** (`matchOver`): `stoppedAtRef` captura el instante y `elapsed` queda fijo. La hora 24 h del bloque derecho sigue actualizándose.

## Fin del partido por mayoría de sets

- `SETS_TO_WIN = Math.floor(MAX_SETS / 2) + 1` (con `MAX_SETS = 3` → **2**).
- `matchOver` pasa a `true` en cuanto un jugador llega a `SETS_TO_WIN` sets ganados,
  sin jugar el resto de los sets (o cuando se completan los `MAX_SETS`).
- `homeSetsWon` / `awaySetsWon` cuentan **solo sets cerrados**
  (`sets.slice(0, home.currentSetIndex)`), no el set en juego.
- Al declararse ganador: botones de punto `disabled`, modal de ganador y cronómetro congelado.

## Verificación

- `tsc --noEmit`: sin errores.
- Render de maqueta a 340×340: altos 40/60/60 correctos, barra azul marino con `12:34` (izq.) y `21:07` (der.) en gris, botones S/R sin tapar el texto.
