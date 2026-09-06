import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useReducer } from "react";

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
const POINTS_TIEBREAK = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "GM"];//GM = Game
const POINTS_DEUCE = ["DC", "AD", "GM"];//AD = Advantage, DC = Deuce, GM = Game
const MAX_SETS = 3;
const MAX_GAMES = 6;
const MAX_GAMES_TIEBREAK = 7;
const MAX_TIEBREAK_POINTS = 7;
const MAX_SUPER_TIEBREAK_POINTS = 10;
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
function winGame(state: MatchState, which: Side): MatchState {
  const winner = state[which];
  const nextSets = [...winner.sets];
  nextSets[winner.currentSetIndex] = (nextSets[winner.currentSetIndex] ?? 0) + 1;
  return {
    ...state,
    deuce: false,
    serving: other(state.serving),
    home: { ...state.home, gamePoints: 0 },
    away: { ...state.away, gamePoints: 0 },
    [which]: { ...winner, sets: nextSets, gamePoints: 0 },
  };
}

// Suma un punto de game al jugador `which` aplicando las reglas del tenis.
function scorePoint(state: MatchState, which: Side): MatchState {
  const p = state[which];
  const o = state[other(which)];

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
  };
}

function ScoreboardApp() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const [state, dispatch] = useReducer(matchReducer, search, initMatch);
  const { home, away, serving, deuce } = state;

  // Etiqueta de puntos a mostrar: POINTS_DEUCE si estamos en deuce, si no POINTS.
  const pointsLabels = deuce ? POINTS_DEUCE : POINTS;

  const renderNameCell = (name: string) => (
    <div className="col-span-2 flex items-center justify-start overflow-hidden px-1">
      <span className="truncate text-sm font-semibold tracking-tight text-foreground">{name}</span>
    </div>
  );

  // Celda de set: fondo azul eléctrico si está activo, azul marino si ya terminó.
  // Texto gris salvo el del ganador del set, que es blanco.
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
    won = false
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
      } ${won ? "text-white" : "text-muted-foreground"}`}
    >
      {value}
    </div>
  );

  // Columnas 3-8 de la fila: fondo negro. La pelota aparece en la columna 3
  // (primera celda) de la fila del jugador que saca.
  const renderTrackCells = (which: Side, isServing: boolean) =>
    Array.from({ length: 6 }).map((_, idx) => (
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
          <div className="grid h-[160px] w-[320px] grid-cols-9 grid-rows-2 gap-1 p-1">
            {/* Fila local: nombre (col 0-1), primer set (col 2), columnas 3-8 negras */}
            {renderNameCell(home.name)}
            {renderSetCell(
              "home-set-0",
              home.sets[0] ?? 0,
              0,
              home.currentSetIndex === 0,
              "home"
            )}
            {renderTrackCells("home", serving === "home")}

            {/* Fila visitante */}
            {renderNameCell(away.name)}
            {renderSetCell(
              "away-set-0",
              away.sets[0] ?? 0,
              0,
              away.currentSetIndex === 0,
              "away"
            )}
            {renderTrackCells("away", serving === "away")}
          </div>

          <div className="flex h-[160px] w-[320px]">
            <button
              type="button"
              onClick={() => dispatch({ type: "point", which: "home" })}
              onContextMenu={(e) => {
                e.preventDefault();
                dispatch({ type: "undoPoint", which: "home" });
              }}
              className="mt-[2px] mr-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95"
              aria-label="Puntos jugador local"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {pointsLabels[home.gamePoints]}
              </span>
            </button>

            <button
              type="button"
              onClick={() => dispatch({ type: "point", which: "away" })}
              onContextMenu={(e) => {
                e.preventDefault();
                dispatch({ type: "undoPoint", which: "away" });
              }}
              className="mt-[2px] ml-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95"
              aria-label="Puntos jugador visitante"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {pointsLabels[away.gamePoints]}
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
    </main>
  );
}
