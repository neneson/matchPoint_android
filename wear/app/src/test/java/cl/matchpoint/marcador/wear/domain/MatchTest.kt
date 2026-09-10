package cl.matchpoint.marcador.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los casos que la app web nunca tuvo cubiertos: deuce, tie break 7-6, super tie
 * break, cierre de set y fin de partido al mejor de 3.
 */
class MatchTest {

    // --- helpers -----------------------------------------------------------

    private fun estado(rules: Rules = Rules.WEB) =
        MatchState.new(MatchSetup(local = "Rene", visitante = "Nacho", rules = rules))

    private fun MatchState.punto(which: Side, veces: Int = 1): MatchState =
        (1..veces).fold(this) { s, _ -> reduce(s, MatchAction.Point(which)) }

    /** Gana un game en seco (4 puntos seguidos). No sirve dentro de un tie break. */
    private fun MatchState.game(which: Side): MatchState = punto(which, 4)

    /** Lleva el set en curso a `n` games para [which], de a un game en seco. */
    private fun MatchState.games(which: Side, n: Int): MatchState =
        (1..n).fold(this) { s, _ -> s.game(which) }

    /**
     * Deja el set en curso 6-6 (tie break). Hay que **alternar** los games: seis
     * seguidos cerrarían el set 6-0 antes de llegar al empate.
     */
    private fun MatchState.seisIguales(): MatchState =
        (1..6).fold(this) { s, _ -> s.game(Side.HOME).game(Side.AWAY) }

    private fun MatchState.gamesDeSet(set: Int): Pair<Int, Int> =
        (home.sets[set] to away.sets[set])

    // --- game normal -------------------------------------------------------

    @Test
    fun `game en blanco a 4 puntos`() {
        val s = estado().punto(Side.HOME, 3)
        assertEquals("40", s.pointLabel(Side.HOME))
        assertEquals("0", s.pointLabel(Side.AWAY))

        val fin = s.punto(Side.HOME)
        assertEquals(1 to 0, fin.gamesDeSet(0))
        assertEquals(0, fin.home.gamePoints)
        assertEquals(0, fin.away.gamePoints)
    }

    @Test
    fun `el saque cambia de mano al terminar el game`() {
        val s = estado()
        assertEquals(Side.HOME, s.serving)
        assertEquals(Side.AWAY, s.game(Side.HOME).serving)
        assertEquals(Side.HOME, s.game(Side.HOME).game(Side.AWAY).serving)
    }

    @Test
    fun `40-30 no gana el game`() {
        // 40-30: índice 3 contra 2. El siguiente punto sí lo cierra (4 contra 2).
        val s = estado().punto(Side.HOME, 3).punto(Side.AWAY, 2)
        assertFalse(s.deuce)
        assertEquals(0 to 0, s.gamesDeSet(0))
        assertEquals(1 to 0, s.punto(Side.HOME).gamesDeSet(0))
    }

    // --- deuce -------------------------------------------------------------

    @Test
    fun `40-40 entra en deuce y ambos vuelven a DC`() {
        val s = estado().punto(Side.HOME, 3).punto(Side.AWAY, 3)
        assertTrue(s.deuce)
        assertEquals("DC", s.pointLabel(Side.HOME))
        assertEquals("DC", s.pointLabel(Side.AWAY))
    }

    @Test
    fun `ventaja y game desde deuce`() {
        val deuce = estado().punto(Side.HOME, 3).punto(Side.AWAY, 3)

        val ventaja = deuce.punto(Side.HOME)
        assertEquals("AD", ventaja.pointLabel(Side.HOME))
        assertEquals("DC", ventaja.pointLabel(Side.AWAY))

        val game = ventaja.punto(Side.HOME)
        assertEquals(1 to 0, game.gamesDeSet(0))
        assertFalse("el deuce se apaga al cerrar el game", game.deuce)
    }

    @Test
    fun `ventaja perdida vuelve a deuce`() {
        val ventaja = estado().punto(Side.HOME, 3).punto(Side.AWAY, 3).punto(Side.HOME)
        val vuelta = ventaja.punto(Side.AWAY)

        assertTrue(vuelta.deuce)
        assertEquals("DC", vuelta.pointLabel(Side.HOME))
        assertEquals("DC", vuelta.pointLabel(Side.AWAY))
        assertEquals("no se ganó ningún game", 0 to 0, vuelta.gamesDeSet(0))
    }

    @Test
    fun `deuce largo termina cuando alguien saca dos seguidas`() {
        var s = estado().punto(Side.HOME, 3).punto(Side.AWAY, 3)
        repeat(5) { s = s.punto(Side.HOME).punto(Side.AWAY) } // 5 idas y vueltas
        assertTrue(s.deuce)
        assertEquals(0 to 0, s.gamesDeSet(0))

        s = s.punto(Side.AWAY).punto(Side.AWAY)
        assertEquals(0 to 1, s.gamesDeSet(0))
    }

    // --- cierre de set -----------------------------------------------------

    @Test
    fun `set 6-0 cierra y ambos avanzan de set`() {
        val s = estado().games(Side.HOME, 6)
        assertEquals(6 to 0, s.gamesDeSet(0))
        assertEquals(1, s.home.currentSetIndex)
        assertEquals("ambos avanzan juntos", 1, s.away.currentSetIndex)
        assertEquals(1, s.homeSetsWon)
        assertFalse(s.matchOver)
    }

    @Test
    fun `6-5 no cierra el set`() {
        val s = estado().games(Side.HOME, 5).games(Side.AWAY, 5).game(Side.HOME)
        assertEquals(6 to 5, s.gamesDeSet(0))
        assertEquals("falta diferencia de 2", 0, s.home.currentSetIndex)
        assertFalse(s.tiebreak)
    }

    @Test
    fun `7-5 cierra el set`() {
        val s = estado().games(Side.HOME, 5).games(Side.AWAY, 5).games(Side.HOME, 2)
        assertEquals(7 to 5, s.gamesDeSet(0))
        assertEquals(1, s.home.currentSetIndex)
    }

    // --- tie break ---------------------------------------------------------

    @Test
    fun `6-6 activa el tie break y los puntos pasan a ser el numero real`() {
        val s = estado().seisIguales()
        assertTrue(s.tiebreak)
        assertFalse(s.superTiebreak)
        assertEquals(7, s.tiebreakTarget)

        val tres = s.punto(Side.HOME, 3)
        assertEquals("3", tres.pointLabel(Side.HOME))
        assertEquals("0", tres.pointLabel(Side.AWAY))
    }

    @Test
    fun `tie break a 7 cierra el set 7-6`() {
        val s = estado().seisIguales().punto(Side.HOME, 7)
        assertEquals(7 to 6, s.gamesDeSet(0))
        assertEquals(1, s.home.currentSetIndex)
        assertFalse(s.tiebreak)
        assertEquals(1, s.homeSetsWon)
    }

    @Test
    fun `tie break 7-6 no alcanza, hace falta diferencia de 2`() {
        val tb = estado().seisIguales()
        val s = tb.punto(Side.HOME, 6).punto(Side.AWAY, 6).punto(Side.HOME)

        assertEquals("7", s.pointLabel(Side.HOME))
        assertEquals("6", s.pointLabel(Side.AWAY))
        assertTrue("sigue el tie break", s.tiebreak)
        assertEquals(6 to 6, s.gamesDeSet(0))

        val fin = s.punto(Side.HOME)
        assertEquals("8-6 cierra", 7 to 6, fin.gamesDeSet(0))
        assertEquals(1, fin.home.currentSetIndex)
    }

    // --- fin del partido ---------------------------------------------------

    @Test
    fun `dos sets de tres terminan el partido`() {
        val s = estado().games(Side.HOME, 6).games(Side.HOME, 6)
        assertEquals(2, s.homeSetsWon)
        assertTrue(s.matchOver)
        assertEquals(Side.HOME, s.winner)
        assertEquals("Rene", s.of(s.winner!!).name)
        assertEquals("no se juega el tercer set", 0, s.home.sets[2])
    }

    @Test
    fun `partido en juego no tiene ganador`() {
        val s = estado().games(Side.HOME, 6)
        assertFalse(s.matchOver)
        assertNull(s.winner)
    }

    @Test
    fun `terminado el partido, el marcador se congela`() {
        val fin = estado().games(Side.HOME, 6).games(Side.HOME, 6)
        assertEquals(fin, fin.punto(Side.AWAY, 10))
        assertEquals(fin, reduce(fin, MatchAction.UndoPoint(Side.HOME)))
    }

    @Test
    fun `partido a tres sets lo gana quien gana el ultimo`() {
        val s = estado()
            .games(Side.HOME, 6)   // set 0: 6-0 Rene
            .games(Side.AWAY, 6)   // set 1: 0-6 Nacho
            .games(Side.AWAY, 6)   // set 2: 0-6 Nacho
        assertEquals(1, s.homeSetsWon)
        assertEquals(2, s.awaySetsWon)
        assertEquals(Side.AWAY, s.winner)
    }

    // --- super tie break (opcional, apagado por defecto) --------------------

    @Test
    fun `sin la opcion, el set decisivo es un set normal`() {
        val s = estado().games(Side.HOME, 6).games(Side.AWAY, 6)
        assertFalse("la web no implementa el super tie break", s.tiebreak)
        assertEquals(2, s.closedSets)
    }

    @Test
    fun `con la opcion, el set decisivo arranca como super tie break a 11`() {
        val reglas = Rules.WEB.copy(superTiebreak = true)
        val s = estado(reglas).games(Side.HOME, 6).games(Side.AWAY, 6)

        assertTrue(s.tiebreak)
        assertTrue(s.superTiebreak)
        assertEquals(11, s.tiebreakTarget)
        assertEquals(2, s.home.currentSetIndex)

        val casi = s.punto(Side.HOME, 10).punto(Side.AWAY, 10) // 10-10
        assertTrue("a 11 con 10-10 no basta", casi.punto(Side.HOME).tiebreak)

        val fin = casi.punto(Side.HOME).punto(Side.HOME) // 12-10
        assertTrue(fin.matchOver)
        assertEquals(Side.HOME, fin.winner)
    }

    @Test
    fun `el super tie break no se activa si el partido ya terminó`() {
        val reglas = Rules.WEB.copy(superTiebreak = true)
        val s = estado(reglas).games(Side.HOME, 6).games(Side.HOME, 6)
        assertTrue(s.matchOver)
        assertFalse(s.tiebreak)
    }

    // --- correcciones manuales ---------------------------------------------

    @Test
    fun `undo baja un punto y no pasa de cero`() {
        val s = estado().punto(Side.HOME, 2)
        val menos = reduce(s, MatchAction.UndoPoint(Side.HOME))
        assertEquals("15", menos.pointLabel(Side.HOME))

        val piso = reduce(reduce(menos, MatchAction.UndoPoint(Side.HOME)), MatchAction.UndoPoint(Side.HOME))
        assertEquals(0, piso.home.gamePoints)
    }

    @Test
    fun `resetGame limpia puntos y deuce pero deja los games`() {
        val s = estado().game(Side.HOME).punto(Side.HOME, 3).punto(Side.AWAY, 3)
        assertTrue(s.deuce)

        val r = reduce(s, MatchAction.ResetGame)
        assertFalse(r.deuce)
        assertEquals(0, r.home.gamePoints)
        assertEquals(0, r.away.gamePoints)
        assertEquals("los games no se tocan", 1 to 0, r.gamesDeSet(0))
    }

    @Test
    fun `editar games a mano no baja de cero`() {
        val s = reduce(estado(), MatchAction.SetGames(Side.HOME, 0, -1))
        assertEquals(0, s.home.sets[0])

        val mas = reduce(s, MatchAction.SetGames(Side.HOME, 0, 3))
        assertEquals(3, mas.home.sets[0])
    }

    @Test
    fun `toggleServe alterna el saque`() {
        val s = estado()
        assertEquals(Side.AWAY, reduce(s, MatchAction.ToggleServe).serving)
        assertEquals(Side.HOME, reduce(reduce(s, MatchAction.ToggleServe), MatchAction.ToggleServe).serving)
    }

    // --- cronómetro --------------------------------------------------------

    @Test
    fun `formatElapsed usa MM_SS y agrega la hora recién pasada`() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("00:05", formatElapsed(5_000))
        assertEquals("01:00", formatElapsed(60_000))
        assertEquals("59:59", formatElapsed(3_599_000))
        assertEquals("1:00:00", formatElapsed(3_600_000))
        assertEquals("2:07:03", formatElapsed(7_623_000))
        assertEquals("00:00", formatElapsed(-5_000))
    }
}
