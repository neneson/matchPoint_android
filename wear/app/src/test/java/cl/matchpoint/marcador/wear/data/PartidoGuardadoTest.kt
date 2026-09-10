package cl.matchpoint.marcador.wear.data

import cl.matchpoint.marcador.wear.domain.MatchAction
import cl.matchpoint.marcador.wear.domain.MatchSetup
import cl.matchpoint.marcador.wear.domain.MatchState
import cl.matchpoint.marcador.wear.domain.Rules
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.domain.reduce
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que se guarda en DataStore tiene que volver **idéntico**: si el JSON pierde el
 * tie break o el índice de set, el partido se retoma mal y no hay forma de notarlo.
 */
class PartidoGuardadoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun idaYVuelta(p: PartidoGuardado): PartidoGuardado =
        json.decodeFromString(
            PartidoGuardado.serializer(),
            json.encodeToString(PartidoGuardado.serializer(), p),
        )

    @Test
    fun `un partido en tie break vuelve igual del JSON`() {
        var m = MatchState.new(MatchSetup(local = "Rene", visitante = "Nacho"))
        repeat(6) { // 6-6 alternando games
            repeat(4) { m = reduce(m, MatchAction.Point(Side.HOME)) }
            repeat(4) { m = reduce(m, MatchAction.Point(Side.AWAY)) }
        }
        repeat(5) { m = reduce(m, MatchAction.Point(Side.HOME)) }
        assertTrue(m.tiebreak)

        val vuelta = idaYVuelta(PartidoGuardado(m, startedAt = 1_700_000_000_000, stoppedAt = null))
        assertEquals(m, vuelta.match)
        assertEquals(1_700_000_000_000, vuelta.startedAt)
        assertNull(vuelta.stoppedAt)
    }

    @Test
    fun `las reglas y el cronometro detenido tambien sobreviven`() {
        val reglas = Rules.WEB.copy(superTiebreak = true, maxSets = 5)
        val m = MatchState.new(MatchSetup(rules = reglas))
        val vuelta = idaYVuelta(PartidoGuardado(m, startedAt = 1_000, stoppedAt = 9_999))

        assertEquals(reglas, vuelta.match.rules)
        assertEquals(5, vuelta.match.home.sets.size)
        assertEquals(9_999L, vuelta.stoppedAt)
    }

    @Test
    fun `un JSON de otra version se descarta en vez de reventar`() {
        val roto = """{"match":{"home":{"name":"x"}},"startedAt":1}"""
        val leido = runCatching { json.decodeFromString(PartidoGuardado.serializer(), roto) }.getOrNull()
        assertNull("debe devolver null, no propagar la excepción", leido)
    }
}
