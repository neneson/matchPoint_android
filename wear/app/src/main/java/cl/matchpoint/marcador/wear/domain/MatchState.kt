package cl.matchpoint.marcador.wear.domain

import kotlinx.serialization.Serializable

/**
 * Lógica de tenis portada 1:1 desde `src/routes/_authenticated/partido.tsx:24-193`
 * (la app web). Es Kotlin puro: sin Android, sin Compose, sin corrutinas — para que
 * se pueda probar en la JVM y para que la UI del reloj (Fase 3) sea sólo pintura.
 *
 * Diferencias deliberadas respecto del TypeScript, todas anotadas donde ocurren:
 *  1. `point`/`undoPoint` no hacen nada con el partido terminado. En la web eso lo
 *     impedía el `disabled` del botón, no el reducer; aquí no hay botón que confiar.
 *  2. Las etiquetas de tie break se generan (el arreglo de la web llegaba sólo a "10",
 *     y sobre eso caía en el número crudo por el `??`).
 *  3. Las clases llevan `@Serializable`: desde la Fase 4 el partido se persiste en
 *     DataStore tal cual, sin un DTO paralelo que se desincronice. kotlinx.serialization
 *     es un plugin de compilación, así que el dominio sigue sin dependencias de Android.
 *  4. [Rules.superTiebreak] existe como opción apagada por defecto: en la web la
 *     constante `MAX_SUPER_TIEBREAK_POINTS` está declarada pero **nunca se usa**.
 *     Con el valor por omisión el comportamiento es idéntico al de la web.
 */

@Serializable
enum class Side {
    HOME,
    AWAY;

    val other: Side get() = if (this == HOME) AWAY else HOME
}

/** Reglas del partido. Los valores por defecto son los de la app web. */
@Serializable
data class Rules(
    val maxSets: Int = 3,
    val maxGames: Int = 6,
    val tiebreakPoints: Int = 7,
    val superTiebreakPoints: Int = 11,
    val minTiebreakDiff: Int = 2,
    val minGameDiff: Int = 2,
    val minSetDiff: Int = 2,
    /**
     * Si está activo, el set decisivo (el último de [maxSets]) se juega como super
     * tie break a [superTiebreakPoints] en vez de como set normal. **Apagado por
     * defecto**: la web no lo implementa.
     */
    val superTiebreak: Boolean = false,
) {
    /** Al mejor de [maxSets]: gana quien se lleve la mayoría, sin jugar el resto. */
    val setsToWin: Int get() = maxSets / 2 + 1

    companion object {
        val WEB = Rules()

        /** Etiquetas de un game normal. `GM` = game. */
        val POINTS = listOf("0", "15", "30", "40", "GM")

        /** Etiquetas en deuce. `DC` = deuce, `AD` = ventaja. */
        val POINTS_DEUCE = listOf("DC", "AD", "GM")
    }
}

@Serializable
data class Player(
    val name: String,
    /** Games ganados en cada set. Índice = número de set. */
    val sets: List<Int>,
    /**
     * En game normal y en deuce es un **índice** de [Rules.POINTS] / [Rules.POINTS_DEUCE].
     * En tie break es el **puntaje real** (0, 1, 2, …).
     */
    val gamePoints: Int = 0,
    /** Set que se está jugando. Ambos jugadores avanzan juntos al cerrarse un set. */
    val currentSetIndex: Int = 0,
)

/** Cómo empieza el partido. Equivale a los search params de la ruta `/partido`. */
@Serializable
data class MatchSetup(
    val local: String = "Local",
    val visitante: String = "Visitante",
    val saca: Side = Side.HOME,
    val rules: Rules = Rules.WEB,
)

@Serializable
data class MatchState(
    val home: Player,
    val away: Player,
    val serving: Side,
    /** Deuce (40-40) activo: [Player.gamePoints] indexa [Rules.POINTS_DEUCE]. */
    val deuce: Boolean = false,
    /** Tie break activo (6-6 en el set): [Player.gamePoints] es el puntaje real. */
    val tiebreak: Boolean = false,
    /** El tie break en curso es el super tie break del set decisivo. */
    val superTiebreak: Boolean = false,
    val rules: Rules = Rules.WEB,
) {
    fun of(which: Side): Player = if (which == Side.HOME) home else away

    /** Puntos necesarios para cerrar el tie break en curso. */
    val tiebreakTarget: Int
        get() = if (superTiebreak) rules.superTiebreakPoints else rules.tiebreakPoints

    /** Sets ya cerrados: ambos avanzan `currentSetIndex` juntos en [winGame]. */
    val closedSets: Int get() = home.currentSetIndex

    val homeSetsWon: Int get() = setsWonBy(home, away)
    val awaySetsWon: Int get() = setsWonBy(away, home)

    private fun setsWonBy(p: Player, rival: Player): Int =
        (0 until closedSets).count { i -> (p.sets.getOrNull(i) ?: 0) > (rival.sets.getOrNull(i) ?: 0) }

    /**
     * Terminado: alguien llegó a [Rules.setsToWin] (se declara ganador sin jugar el
     * resto) o ya se jugaron todos los sets.
     */
    val matchOver: Boolean
        get() = homeSetsWon >= rules.setsToWin ||
            awaySetsWon >= rules.setsToWin ||
            closedSets >= rules.maxSets

    /**
     * ¿Se jugó aunque sea un punto? Distingue un partido de verdad de uno recién creado,
     * que es lo que decide si la app abre en el marcador o en la preparación.
     */
    val empezado: Boolean
        get() = closedSets > 0 ||
            home.gamePoints > 0 || away.gamePoints > 0 ||
            home.sets.any { it > 0 } || away.sets.any { it > 0 }

    /** Ganador del partido, o `null` si sigue en juego. */
    val winner: Side?
        get() = if (!matchOver) null else if (homeSetsWon > awaySetsWon) Side.HOME else Side.AWAY

    /** Lo que va escrito en el botón grande de puntos: tie break > deuce > normal. */
    fun pointLabel(which: Side): String {
        val points = of(which).gamePoints
        return when {
            tiebreak -> points.toString()
            deuce -> Rules.POINTS_DEUCE.getOrNull(points) ?: points.toString()
            else -> Rules.POINTS.getOrNull(points) ?: points.toString()
        }
    }

    companion object {
        fun new(setup: MatchSetup = MatchSetup()): MatchState {
            val ceros = List(setup.rules.maxSets) { 0 }
            return MatchState(
                home = Player(name = setup.local, sets = ceros),
                away = Player(name = setup.visitante, sets = ceros),
                serving = setup.saca,
                rules = setup.rules,
            )
        }
    }
}

sealed interface MatchAction {
    /** Punto para [which]. */
    data class Point(val which: Side) : MatchAction

    /** Corrección: baja un punto, sin tocar sets ni deuce. */
    data class UndoPoint(val which: Side) : MatchAction

    /** Editar a mano los games de un set. */
    data class SetGames(val which: Side, val setIndex: Int, val delta: Int) : MatchAction

    /** Volver el game a 0-0 (sale del deuce; no toca el tie break, igual que la web). */
    data object ResetGame : MatchAction

    /** Cambiar de mano la pelota de saque. */
    data object ToggleServe : MatchAction
}

private fun MatchState.withPlayer(which: Side, player: Player): MatchState =
    if (which == Side.HOME) copy(home = player) else copy(away = player)

/** `sets` es del largo de `maxSets`, pero un índice fuera de rango no debe reventar. */
private fun List<Int>.setAt(index: Int, value: Int): List<Int> {
    val out = toMutableList()
    while (out.size <= index) out.add(0)
    out[index] = value
    return out
}

/**
 * Gana el game: +1 al set activo del ganador, se reinician puntos y deuce, y se
 * alterna la pelota de saque. Si con este game quedan 6-6 se activa el tie break en
 * vez de cerrar el set. El set se cierra (avanza `currentSetIndex` de ambos) al
 * llegar a 6+ games con 2 de diferencia, o al ganar el tie break.
 */
internal fun winGame(state: MatchState, which: Side): MatchState {
    val rules = state.rules
    val winner = state.of(which)
    val loser = state.of(which.other)
    val wasTiebreak = state.tiebreak

    val setIndex = winner.currentSetIndex
    val winnerGames = (winner.sets.getOrNull(setIndex) ?: 0) + 1
    val nextSets = winner.sets.setAt(setIndex, winnerGames)
    val loserGames = loser.sets.getOrNull(setIndex) ?: 0

    val startsTiebreak = !wasTiebreak && winnerGames == rules.maxGames && loserGames == rules.maxGames
    val winsSet = wasTiebreak ||
        (winnerGames >= rules.maxGames && winnerGames - loserGames >= rules.minSetDiff)
    val nextSetIndex = if (winsSet) setIndex + 1 else setIndex

    val base = state
        .withPlayer(which, winner.copy(sets = nextSets, gamePoints = 0, currentSetIndex = nextSetIndex))
        .withPlayer(which.other, loser.copy(gamePoints = 0, currentSetIndex = nextSetIndex))
        .copy(deuce = false, tiebreak = false, superTiebreak = false, serving = state.serving.other)

    // Set decisivo jugado como super tie break: sólo si el partido sigue vivo.
    val startsSuper = rules.superTiebreak &&
        winsSet &&
        nextSetIndex == rules.maxSets - 1 &&
        !base.matchOver

    return base.copy(tiebreak = startsTiebreak || startsSuper, superTiebreak = startsSuper)
}

/** Suma un punto de game a [which] aplicando las reglas del tenis. */
internal fun scorePoint(state: MatchState, which: Side): MatchState {
    val rules = state.rules
    val p = state.of(which)
    val o = state.of(which.other)

    if (state.tiebreak) {
        // Puntaje real (no índice): gana quien llegue al objetivo con 2 de diferencia.
        val next = p.gamePoints + 1
        return if (next >= state.tiebreakTarget && next - o.gamePoints >= rules.minTiebreakDiff) {
            winGame(state, which)
        } else {
            state.withPlayer(which, p.copy(gamePoints = next))
        }
    }

    if (state.deuce) {
        // POINTS_DEUCE = ["DC", "AD", "GM"]
        return when {
            // Estaban en deuce (DC): el que anota toma la ventaja (AD, índice 1).
            p.gamePoints == 0 && o.gamePoints == 0 -> state.withPlayer(which, p.copy(gamePoints = 1))
            // Tenía la ventaja y vuelve a anotar: gana el game.
            p.gamePoints == 1 -> winGame(state, which)
            // El rival tenía la ventaja y anota el otro: ambos vuelven a DC.
            else -> state.copy(
                home = state.home.copy(gamePoints = 0),
                away = state.away.copy(gamePoints = 0),
            )
        }
    }

    // Modo normal. POINTS = ["0", "15", "30", "40", "GM"]
    val next = p.gamePoints + 1

    // 40-40: se activa el deuce y ambos quedan en DC (índice 0).
    if (next == 3 && o.gamePoints == 3) {
        return state.copy(
            deuce = true,
            home = state.home.copy(gamePoints = 0),
            away = state.away.copy(gamePoints = 0),
        )
    }

    // Índice 4 con 2 de diferencia en los índices => gana el game.
    if (next == 4 && o.gamePoints <= next - rules.minGameDiff) {
        return winGame(state, which)
    }

    return state.withPlayer(which, p.copy(gamePoints = minOf(Rules.POINTS.size - 1, next)))
}

/** Reducer del partido. Equivalente a `matchReducer` del TypeScript. */
fun reduce(state: MatchState, action: MatchAction): MatchState = when (action) {
    // Con el partido terminado el marcador se congela. En la web esto lo hacía el
    // `disabled` de los botones; aquí vive en el reducer, que es lo que se prueba.
    is MatchAction.Point ->
        if (state.matchOver) state else scorePoint(state, action.which)

    is MatchAction.UndoPoint ->
        if (state.matchOver) {
            state
        } else {
            val p = state.of(action.which)
            state.withPlayer(action.which, p.copy(gamePoints = maxOf(0, p.gamePoints - 1)))
        }

    is MatchAction.SetGames -> {
        val p = state.of(action.which)
        val actual = p.sets.getOrNull(action.setIndex) ?: 0
        state.withPlayer(action.which, p.copy(sets = p.sets.setAt(action.setIndex, maxOf(0, actual + action.delta))))
    }

    MatchAction.ResetGame -> state.copy(
        deuce = false,
        home = state.home.copy(gamePoints = 0),
        away = state.away.copy(gamePoints = 0),
    )

    MatchAction.ToggleServe -> state.copy(serving = state.serving.other)
}

/** Tiempo del partido: `MM:SS` y, pasada la hora, `H:MM:SS`. */
fun formatElapsed(ms: Long): String {
    val total = maxOf(0L, ms / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}
