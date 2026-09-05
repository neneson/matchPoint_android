import { createFileRoute } from "@tanstack/react-router";
import { useState, useCallback } from "react";

export const Route = createFileRoute("/")({
  component: ScoreboardApp,
  head: () => ({
    meta: [
      { title: "Smartwatch Scoreboard" },
      { name: "description", content: "Tennis scoreboard for 320x320 smartwatch" },
      { property: "og:title", content: "Smartwatch Scoreboard" },
      { property: "og:description", content: "Tennis scoreboard for 320x320 smartwatch" },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
});

const POINTS = ["0", "15", "30", "40", "AD"];

type Player = {
  name: string;
  sets: number[];
  gamePoints: number;
  currentSetIndex: number;
};

function ScoreboardApp() {
  const [home, setHome] = useState<Player>({
    name: "JuanCo",
    sets: [6, 5, 5],
    gamePoints: 3, // 40
    currentSetIndex: 2,
  });

  const [away, setAway] = useState<Player>({
    name: "Rene",
    sets: [1, 7, 3],
    gamePoints: 1, // 15
    currentSetIndex: 2,
  });

  const [serving, setServing] = useState<"home" | "away">("home");

  const updatePoints = useCallback(
    (which: "home" | "away", delta: number) => {
      const player = which === "home" ? home : away;
      const setPlayer = which === "home" ? setHome : setAway;
      const opponent = which === "home" ? away : home;
      const setOpponent = which === "home" ? setAway : setHome;

      const nextPoints = Math.max(0, Math.min(POINTS.length - 1, player.gamePoints + delta));

      setPlayer({ ...player, gamePoints: nextPoints });
      setServing(which);

      // Simplified deuce/advance handling visualization: when a player reaches AD and the other is at 40, reset opponent
      if (nextPoints === 4 && opponent.gamePoints === 3) {
        setOpponent({ ...opponent, gamePoints: 2 });
      }
    },
    [home, away]
  );

  const updateSet = useCallback(
    (which: "home" | "away", setIndex: number, delta: number) => {
      const player = which === "home" ? home : away;
      const setPlayer = which === "home" ? setHome : setAway;
      const nextSets = [...player.sets];
      const current = nextSets[setIndex] ?? 0;
      nextSets[setIndex] = Math.max(0, current + delta);
      setPlayer({ ...player, sets: nextSets });
    },
    [home, away]
  );

  const resetGame = useCallback(() => {
    setHome({ ...home, gamePoints: 0 });
    setAway({ ...away, gamePoints: 0 });
  }, [home, away]);

  const renderNameCell = (name: string) => (
    <div className="col-span-2 flex items-center justify-start overflow-hidden px-1">
      <span className="truncate text-sm font-semibold tracking-tight text-foreground">{name}</span>
    </div>
  );

  const renderSetCell = (
    value: number,
    setIndex: number,
    isActive: boolean,
    which: "home" | "away"
  ) => (
    <button
      type="button"
      onClick={() => updateSet(which, setIndex, 1)}
      onContextMenu={(e) => {
        e.preventDefault();
        updateSet(which, setIndex, -1);
      }}
      className={`flex items-center justify-center rounded-sm text-lg font-bold transition-colors active:scale-95 ${
        isActive
          ? "bg-set-active text-primary-foreground"
          : "bg-set-done text-primary-foreground/90"
      }`}
    >
      {value}
    </button>
  );

  return (
    <main className="flex min-h-screen items-center justify-center bg-black p-4">
      <div
        className="relative overflow-hidden rounded-[3rem] border-[10px] border-watch-case bg-background shadow-2xl"
        style={{ width: 340, height: 340 }}
      >
        {/* Watch screen content */}
        <div className="absolute inset-[10px] overflow-hidden rounded-[2.2rem] bg-background">
          {/* Top half: 320 x 160, 2 rows x 9 columns */}
          <div className="grid h-[160px] w-[320px] grid-cols-9 grid-rows-2 gap-1 p-1">
            {/* Home row */}
            {renderNameCell(home.name)}
            {home.sets.map((score, idx) =>
              renderSetCell(score, idx, idx === home.currentSetIndex, "home")
            )}
            {Array.from({ length: 6 - home.sets.length }).map((_, idx) => (
              <div key={`home-empty-${idx}`} className="rounded-sm bg-muted" />
            ))}
            {/* Server indicator: tennis ball */}
            <div className="relative flex items-center justify-center">
              {serving === "home" && (
                <div className="absolute h-4 w-4 rounded-full bg-ball shadow-sm" />
              )}
            </div>

            {/* Away row */}
            {renderNameCell(away.name)}
            {away.sets.map((score, idx) =>
              renderSetCell(score, idx, idx === away.currentSetIndex, "away")
            )}
            {Array.from({ length: 6 - away.sets.length }).map((_, idx) => (
              <div key={`away-empty-${idx}`} className="rounded-sm bg-muted" />
            ))}
            <div className="relative flex items-center justify-center">
              {serving === "away" && (
                <div className="absolute h-4 w-4 rounded-full bg-ball shadow-sm" />
              )}
            </div>
          </div>

          {/* Bottom half: two 160x160 quadrants */}
          <div className="flex h-[160px] w-[320px]">
            {/* Home score quadrant */}
            <button
              type="button"
              onClick={() => updatePoints("home", 1)}
              onContextMenu={(e) => {
                e.preventDefault();
                updatePoints("home", -1);
              }}
              className="mt-[2px] mr-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95"
              aria-label="Puntos jugador local"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {POINTS[home.gamePoints]}
              </span>
            </button>

            {/* Away score quadrant */}
            <button
              type="button"
              onClick={() => updatePoints("away", 1)}
              onContextMenu={(e) => {
                e.preventDefault();
                updatePoints("away", -1);
              }}
              className="mt-[2px] ml-[1px] flex h-[160px] w-[160px] items-center justify-center rounded-[1.5rem] bg-card text-card-foreground transition-transform active:scale-95"
              aria-label="Puntos jugador visitante"
            >
              <span className="text-[5rem] font-bold leading-none tracking-tighter">
                {POINTS[away.gamePoints]}
              </span>
            </button>
          </div>

          {/* Reset button */}
          <button
            type="button"
            onClick={resetGame}
            className="absolute top-1 right-1 flex h-6 w-6 items-center justify-center rounded-full bg-muted text-[10px] font-medium text-muted-foreground"
            aria-label="Reiniciar puntos"
          >
            R
          </button>
        </div>
      </div>
    </main>
  );
}
