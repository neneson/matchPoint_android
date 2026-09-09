import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useReducer, useRef, useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export const Route = createFileRoute("/_authenticated/partido")({
  validateSearch: (search: Record<string, unknown>) => ({
    local: typeof search["local"] === "string" ? (search["local"] as string) : "Local",
    visitante: typeof search["visitante"] === "string" ? (search["visitante"] as string) : "Visitante",
    saca: search["saca"] === "visitante" ? ("visitante" as const) : ("local" as const),
  }),
  component: ScoreboardApp,
  head: () => ({
    meta: [
      { title: "Marcador en juego | Marcador de Tenis" },
      { name: "description", content: "Marcador de tenis para reloj inteligente de 320x320." },
      { property: "og:title", content: "Marcador en juego | Marcador de Tenis" },
      { property: "og:description", content: "Marcador de tenis para reloj inteligente de 320x320." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});

const POINTS = ["0", "15", "30", "40", "GM"];//GM = Game
const POINTS_TIEBREAK = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"];//GM = Game
const POINTS_DEUCE = ["DC", "AD", "GM"];//AD = Advantage, DC = Deuce, GM = Game
const MAX_SETS = 3;
// Al mejor de MAX_SETS: gana quien se lleve la mayoría de los sets, sin jugar el
// resto (división entera de MAX_SETS/2, + 1). Con MAX_SETS = 3 → 2 sets.
const SETS_TO_WIN = Math.floor(MAX_SETS / 2) + 1;
const MAX_GAMES = 6;
const MAX_GAMES_TIEBREAK = 7;
const MAX_TIEBREAK_POINTS = 7;
const MAX_SUPER_TIEBREAK_POINTS = 11;
const MIN_TIEBREAK_DIFF = 2;
const MIN_GAME_DIFF = 2;
const MIN_SET_DIFF = 2;
const GAMES_CHANGE_SIDE = 5;

type Side = "home" | "away";

type Player = {
  name: string;
  sets: number[];
  gamePoints: number;
  currentSetIndex: number;
};

type MatchState = {
  home: Player;
  away: Player;
  serving: Side;
  // Deuce (40-40) activo: mientras lo esté, gamePoints indexa POINTS_DEUCE en vez de POINTS.
  deuce: boolean;
  // Tie break activo (6-6 en el set): mientras lo esté, gamePoints es el puntaje
  // real del tie break (0,1,2,...) en vez de un índice de POINTS.
  tiebreak: boolean;
};

type MatchAction =
  | { type: "point"; which: Side }
  | { type: "undoPoint"; which: Side }
  | { type: "set"; which: Side; setIndex: number; delta: number }
  | { type: "resetGame" }
  | { type: "toggleServe" };

const other = (which: Side): Side => (which === "home" ? "away" : "home");

const withPlayer = (state: MatchState, which: Side, player: Player): MatchState =>
  which === "home" ? { ...state, home: player } : { ...state, away: player };

// Gana el game: +1 al set activo del ganador, se reinician puntos y deuce, y
// se alterna la pelota de saque (local <-> visitante) al sumar el punto de set.
// Si con este game ambos quedan 6-6, en vez de terminar el set se activa el tie break.
// Se cierra el set (avanza currentSetIndex de ambos) al llegar a 6+ games con 2 de
// diferencia, o al ganar el tie break (7-6).
function winGame(state: MatchState, which: Side): MatchState {
  const winner = state[which];
  const loser = state[other(which)];
  const wasTiebreak = state.tiebreak;

  const nextSets = [...winner.sets];
  const winnerGames = (nextSets[winner.currentSetIndex] ?? 0) + 1;
  nextSets[winner.currentSetIndex] = winnerGames;
  const loserGames = loser.sets[winner.currentSetIndex] ?? 0;

  const startsTiebreak = !wasTiebreak && winnerGames === MAX_GAMES && loserGames === MAX_GAMES;
  const winsSet = wasTiebreak || (winnerGames >= MAX_GAMES && winnerGames - loserGames >= MIN_SET_DIFF);
  const nextSetIndex = winsSet ? winner.currentSetIndex + 1 : winner.currentSetIndex;

  const base = withPlayer(
    withPlayer(state, which, { ...winner, sets: nextSets, gamePoints: 0, currentSetIndex: nextSetIndex }),
    other(which),
    { ...loser, gamePoints: 0, currentSetIndex: nextSetIndex }
  );

  return { ...base, deuce: false, tiebreak: startsTiebreak, serving: other(state.serving) };
}

// Suma un punto de game al jugador `which` aplicando las reglas del tenis.
function scorePoint(state: MatchState, which: Side): MatchState {
  const p = state[which];
  const o = state[other(which)];

  if (state.tiebreak) {
    // Puntaje real (no índice): gana quien llegue a 7 o más con 2 de diferencia.
    const next = p.gamePoints + 1;
    if (next >= MAX_TIEBREAK_POINTS && next - o.gamePoints >= MIN_TIEBREAK_DIFF) {
      return winGame(state, which);
    }
    return withPlayer(state, which, { ...p, gamePoints: next });
  }

  if (state.deuce) {
    // POINTS_DEUCE = ["DC", "AD", "GM"]
    if (p.gamePoints === 0 && o.gamePoints === 0) {
      // Estaban en deuce (DC): el que anota toma la ventaja (AD, índice 1).
      return withPlayer(state, which, { ...p, gamePoints: 1 });
    }
    if (p.gamePoints === 1) {
      // Tenía la ventaja y vuelve a anotar: índice 2 con el rival en 0 => diferencia de 2 => gana el game.
      return winGame(state, which);
    }
    // El rival tenía la ventaja y anota el otro: ambos vuelven a DC (índice 0).
    return {
      ...state,
      home: { ...state.home, gamePoints: 0 },
      away: { ...state.away, gamePoints: 0 },
    };
  }

  // Modo normal. POINTS = ["0", "15", "30", "40", "GM"]
  const next = p.gamePoints + 1;

  if (next === 3 && o.gamePoints === 3) {
    // 40 - 40: se activa el deuce y ambos quedan en DC (índice 0).
    return {
      ...state,
      deuce: true,
      home: { ...state.home, gamePoints: 0 },
      away: { ...state.away, gamePoints: 0 },
    };
  }

  if (next === 4 && o.gamePoints <= next - MIN_GAME_DIFF) {
    // Llega al índice 4 con diferencia de 2 en los índices => gana el game.
    return winGame(state, which);
  }

  return withPlayer(state, which, { ...p, gamePoints: Math.min(POINTS.length - 1, next) });
}

function matchReducer(state: MatchState, action: MatchAction): MatchState {
  switch (action.type) {
    case "point":
      return scorePoint(state, action.which);
    case "undoPoint": {
      // Corrección: baja un índice, sin tocar los sets ni el deuce.
      const p = state[action.which];
      return withPlayer(state, action.which, {
        ...p,
        gamePoints: Math.max(0, p.gamePoints - 1),
      });
    }
    case "set": {
      const p = state[action.which];
      const nextSets = [...p.sets];
      nextSets[action.setIndex] = Math.max(0, (nextSets[action.setIndex] ?? 0) + action.delta);
      return withPlayer(state, action.which, { ...p, sets: nextSets });
    }
    case "resetGame":
      return {
        ...state,
        deuce: false,
        home: { ...state.home, gamePoints: 0 },
        away: { ...state.away, gamePoints: 0 },
      };
    case "toggleServe":
      return { ...state, serving: other(state.serving) };
    default:
      return state;
  }
}

function initMatch(search: { local: string; visitante: string; saca: "local" | "visitante" }): MatchState {
  return {
    home: { name: search.local, sets: [0, 0, 0], gamePoints: 0, currentSetIndex: 0 },
    away: { name: search.visitante, sets: [0, 0, 0], gamePoints: 0, currentSetIndex: 0 },
    serving: search.saca === "visitante" ? "away" : "home",
    deuce: false,
    tiebreak: false,
  };
}

// Tiempo transcurrido del partido: "MM:SS" y, pasada la hora, "H:MM:SS".
function formatElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

// Hora local en formato 24 h "HH:MM".
function formatClock(ts: number): string {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function ScoreboardApp() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const [state, dispatch] = useReducer(matchReducer, search, initMatch);
  const { home, away, serving, deuce, tiebreak } = state;

  // Sets ya cerrados (ambos avanzan `currentSetIndex` juntos en `winGame`); solo
  // esos cuentan para el marcador de sets.
  const closedSets = home.currentSetIndex;
  const homeSetsWon = home.sets
    .slice(0, closedSets)
    .reduce((count, v, i) => count + (v > (away.sets[i] ?? 0) ? 1 : 0), 0);
  const awaySetsWon = away.sets
    .slice(0, closedSets)
    .reduce((count, v, i) => count + (v > (home.sets[i] ?? 0) ? 1 : 0), 0);

  // Partido terminado: alguien llegó a SETS_TO_WIN (se declara ganador sin jugar
  // el resto de los sets) o ya se jugaron los MAX_SETS sets.
  const matchOver =
    homeSetsWon >= SETS_TO_WIN || awaySetsWon >= SETS_TO_WIN || home.currentSetIndex >= MAX_SETS;
  const matchWinnerName = homeSetsWon > awaySetsWon ? home.name : away.name;

  // Reloj del partido. Se arranca en el cliente (evita desajuste de hidratación
  // por SSR); `startedAt` marca el inicio y `now` avanza cada segundo.
  // El tiempo transcurrido se congela al terminar el partido (`stoppedAtRef`
  // captura el `now` de ese instante); la hora del reloj sigue corriendo.
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const t0 = Date.now();
    setStartedAt(t0);
    setNow(t0);
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);
  const stoppedAtRef = useRef<number | null>(null);
  if (matchOver && stoppedAtRef.current == null && startedAt != null) {
    stoppedAtRef.current = now ?? Date.now();
  }
  const elapsedRef = stoppedAtRef.current ?? now;
  const elapsed =
    startedAt != null && elapsedRef != null ? formatElapsed(elapsedRef - startedAt) : "00:00";
  const clock = now != null ? formatClock(now) : "--:--";

  // Etiqueta de puntos a mostrar: tie break > deuce > puntos normales.
  const pointsLabels = tiebreak ? POINTS_TIEBREAK : deuce ? POINTS_DEUCE : POINTS;

  const [modalDismissed, setModalDismissed] = useState(false);
  const modalOpen = matchOver && !modalDismissed;

  const renderNameCell = (name: string) => (
    <div className="col-span-2 flex items-center justify-start overflow-hidden px-1">
      <span className="truncate text-sm font-semibold tracking-tight text-foreground">{name}</span>
    </div>
  );

  // Celda de set: fondo azul eléctrico si está activo, azul marino si ya terminó.
  // Texto gris salvo el del ganador del set, que se muestra en amarillo.
  //
  // NOTA TEMPORAL (2026-09-06): se deshabilitó el clic en las casillas del
  // arreglo `record` (editar el set a mano). La acción "set" del reducer sigue
  // disponible; para reactivar, volver a <button> y descomentar los handlers.
  const renderSetCell = (
    key: string,
    value: number,
    setIndex: number,
    isActive: boolean,
    which: Side,
    won: boolean
  ) => (
    <div
      key={key}
      // onClick={() => dispatch({ type: "set", which, setIndex, delta: 1 })}
      // onContextMenu={(e) => {
      //   e.preventDefault();
      //   dispatch({ type: "set", which, setIndex, delta: -1 });
      // }}
      className={`flex items-center justify-center rounded-sm text-lg font-bold transition-colors ${
        isActive ? "bg-set-active" : "bg-set-done"
      } ${won ? "text-accent" : "text-muted-foreground"}`}
    >
      {value}
    </div>
  );

  // Columnas 5-8 de la fila: fondo negro. La pelota aparece en la primera de
  // estas columnas de la fila del jugador que saca.
  const renderTrackCells = (which: Side, isServing: boolean) =>
    Array.from({ length: 9 - 2 - MAX_SETS }).map((_, idx) => (
      <div
        key={`${which}-track-${idx}`}
        className="relative flex items-center justify-center rounded-sm bg-black"
      >
        {idx === 0 && isServing && (
          <div className="absolute h-4 w-4 rounded-full bg-ball shadow-sm" />
        )}
      </div>
    ));

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-black p-4">
      <div
        className="relative overflow-hidden rounded-[3rem] border-[10px] border-watch-case bg-background shadow-2xl"
        style={{ width: 340, height: 340 }}
      >
        <div className="absolute inset-[10px] overflow-hidden rounded-[2.2rem] bg-background">
          <div className="grid h-[160px] w-[320px] grid-cols-9 grid-rows-[40px_60px_60px] gap-x-1">
            {/* Fila 0 (40px): 3 bloques de 3 columnas. Izquierda = tiempo
                transcurrido, derecha = hora 24 h. Fondo azul marino, texto gris. */}
            <div className="col-span-3 flex items-center justify-center rounded-sm bg-navy pl-7 text-sm font-semibold tabular-nums text-muted-foreground">
              {elapsed}
            </div>
            <div className="col-span-3 rounded-sm bg-navy" />
            <div className="col-span-3 flex items-center justify-center rounded-sm bg-navy pr-7 text-sm font-semibold tabular-nums text-muted-foreground">
              {clock}
            </div>

            {/* Fila local: nombre (col 0-1), sets (col 2-4), columnas 5-8 negras */}
            {renderNameCell(home.name)}
            {Array.from({ length: MAX_SETS }, (_, i) => {
              const homeVal = home.sets[i] ?? 0;
              const awayVal = away.sets[i] ?? 0;
              const finished = home.currentSetIndex !== i;
              return renderSetCell(
                `home-set-${i}`,
                homeVal,
                i,
                home.currentSetIndex === i,
                "home",
                finished && homeVal > awayVal
              );
            })}
            {renderTrackCells("home", serving === "home")}

            {/* Fila visitante */}
            {renderNameCell(away.name)}
            {Array.from({ length: MAX_SETS }, (_, i) => {
              const homeVal = home.sets[i] ?? 0;
              const awayVal = away.sets[i] ?? 0;
              const finished = away.currentSetIndex !== i;
              return renderSetCell(
                `away-set-${i}`,
                awayVal,
                i,
                away.currentSetIndex === i,
                "away",
                finished && awayVal > homeVal
              );
            })}
            {renderTrackCells("away", serving === "away")}
          </div>

          <div className="flex h-[160px] w-[320px]">
            <button
              type="button"
              disabled={matchOver}
              onClick={() => !matchOver && dispatch({ type: "point", which: "home" })}
              onContextMenu={(e) => {
                e.preventDefault();
                if (!matchOver) dispatch({ type: "undoPoint", which: "home" });
              }}
              className="mt-[2px] mr-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95 disabled:opacity-50"
              aria-label="Puntos jugador local"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {pointsLabels[home.gamePoints] ?? home.gamePoints}
              </span>
            </button>

            <button
              type="button"
              disabled={matchOver}
              onClick={() => !matchOver && dispatch({ type: "point", which: "away" })}
              onContextMenu={(e) => {
                e.preventDefault();
                if (!matchOver) dispatch({ type: "undoPoint", which: "away" });
              }}
              className="mt-[2px] ml-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95 disabled:opacity-50"
              aria-label="Puntos jugador visitante"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {pointsLabels[away.gamePoints] ?? away.gamePoints}
              </span>
            </button>
          </div>

          <button
            type="button"
            onClick={() => dispatch({ type: "resetGame" })}
            className="absolute top-1 right-1 flex h-6 w-6 items-center justify-center rounded-full bg-muted text-[10px] font-medium text-muted-foreground"
            aria-label="Reiniciar puntos"
          >
            R
          </button>

          <button
            type="button"
            onClick={() => dispatch({ type: "toggleServe" })}
            className="absolute top-1 left-1 flex h-6 w-6 items-center justify-center rounded-full bg-muted text-[10px] font-medium text-muted-foreground"
            aria-label="Cambiar saque"
          >
            S
          </button>
        </div>
      </div>

      <button
        type="button"
        onClick={() => navigate({ to: "/preparar" })}
        className="text-xs text-muted-foreground underline"
      >
        Nuevo match
      </button>

      <Dialog open={modalOpen} onOpenChange={(open) => setModalDismissed(!open)}>
        <DialogContent className="w-[280px] max-w-[280px] max-h-[280px] gap-3 p-5">
          <DialogHeader>
            <DialogTitle>{matchWinnerName} ganó el partido</DialogTitle>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    </main>
  );
}
