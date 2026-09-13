package cl.matchpoint.marcador.wear.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las garantías de batería del cronómetro, escritas como tests porque son invisibles:
 * una regresión aquí no se ve en pantalla, se ve en el porcentaje de batería tres horas
 * después.
 *
 * El truco para medirlas es el reloj inyectado: cada lectura de `now()` es un despertar
 * del cronómetro, así que contar lecturas es contar trabajo. Con el planificador virtual
 * de `runTest` se pueden simular las dos horas de un partido en milisegundos reales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelBateriaTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    /** Reloj de mentira que además cuenta cuántas veces se lo miró. */
    private class Reloj {
        var ahora = 0L
        var lecturas = 0
        fun leer(): Long {
            lecturas++
            return ahora
        }
    }

    @Test
    fun `sin nadie mirando el cronometro no despierta la CPU`() = runTest(dispatcher) {
        val reloj = Reloj()
        val vm = MatchViewModel(now = reloj::leer)
        runCurrent()

        val alArrancar = reloj.lecturas
        reloj.ahora = 60_000
        advanceTimeBy(60_000)
        runCurrent()

        // Nadie colecciona `elapsed`: en un minuto entero no debe haber mirado el reloj.
        assertEquals(alArrancar, reloj.lecturas)
    }

    @Test
    fun `con la pantalla visible marca un despertar por segundo`() = runTest(dispatcher) {
        val reloj = Reloj()
        val vm = MatchViewModel(now = reloj::leer)
        runCurrent()

        val trabajo = launch { vm.elapsed.collect { } }
        runCurrent()
        val alEmpezar = reloj.lecturas

        repeat(10) {
            reloj.ahora += 1_000
            advanceTimeBy(1_000)
            runCurrent()
        }

        // Dos lecturas por segundo como mucho (una para el texto, otra para alinear el
        // siguiente `delay`); lo que se vigila es que no sea un bucle a la carrera.
        val porSegundo = (reloj.lecturas - alEmpezar) / 10.0
        assertTrue("despertares por segundo: $porSegundo", porSegundo <= 2.5)
        assertEquals("00:10", vm.elapsed.value)

        trabajo.cancel()
    }

    @Test
    fun `al dejar de mirar el cronometro se apaga solo`() = runTest(dispatcher) {
        val reloj = Reloj()
        val vm = MatchViewModel(now = reloj::leer)
        runCurrent()

        val trabajo = launch { vm.elapsed.collect { } }
        runCurrent()
        reloj.ahora += 3_000
        advanceTimeBy(3_000)
        runCurrent()

        // El usuario vuelve al watch face: la UI deja de coleccionar.
        trabajo.cancel()
        reloj.ahora += 5_000
        advanceTimeBy(5_000)
        runCurrent()

        val alSoltar = reloj.lecturas
        reloj.ahora += 600_000
        advanceTimeBy(600_000)
        runCurrent()

        // Diez minutos después, ni una lectura más: esto es lo que antes eran 600
        // despertares dibujando en una pantalla apagada.
        assertEquals(alSoltar, reloj.lecturas)
    }

    @Test
    fun `en ambient solo refresca cuando el sistema despierta la app`() = runTest(dispatcher) {
        val reloj = Reloj()
        val vm = MatchViewModel(now = reloj::leer)
        runCurrent()

        val trabajo = launch { vm.elapsed.collect { } }
        runCurrent()
        vm.setAmbient(true)
        runCurrent()

        val enAmbient = reloj.lecturas
        reloj.ahora += 300_000
        advanceTimeBy(300_000)
        runCurrent()

        // Cinco minutos de pantalla apagada sin un solo tick propio.
        assertEquals(enAmbient, reloj.lecturas)

        // El único refresco es el pulso del sistema, y llega sin segundos.
        vm.tickAmbient()
        runCurrent()
        assertEquals("0:05", vm.elapsed.value)

        trabajo.cancel()
    }

    @Test
    fun `al terminar el partido el cronometro se congela y el bucle termina`() =
        runTest(dispatcher) {
            val reloj = Reloj()
            val vm = MatchViewModel(
                setup = MatchSetup(local = "Rene", visitante = "Nacho"),
                now = reloj::leer,
            )
            runCurrent()

            val trabajo = launch { vm.elapsed.collect { } }
            runCurrent()

            // Dos sets en seco: 4 puntos por game, 6 games por set.
            reloj.ahora = 90_000
            repeat(2 * 6 * 4) { vm.dispatch(MatchAction.Point(Side.HOME)) }
            runCurrent()
            assertTrue(vm.state.value.matchOver)
            assertEquals("01:30", vm.elapsed.value)

            val alCongelar = reloj.lecturas
            reloj.ahora += 120_000
            advanceTimeBy(120_000)
            runCurrent()

            // El tiempo queda clavado y el bucle ya no gira.
            assertEquals("01:30", vm.elapsed.value)
            assertEquals(alCongelar, reloj.lecturas)

            trabajo.cancel()
        }

    @Test
    fun `la pantalla siempre encendida se puede apagar y es el ajuste mas caro`() =
        runTest(dispatcher) {
            val vm = MatchViewModel(now = { 0L })
            runCurrent()

            // Por omisión encendida: es la razón de ser de un marcador de muñeca.
            assertTrue(vm.siempreEncendido.value)

            vm.setSiempreEncendido(false)
            runCurrent()
            assertEquals(false, vm.siempreEncendido.value)

            vm.setSiempreEncendido(true)
            runCurrent()
            assertTrue(vm.siempreEncendido.value)
        }

    @Test
    fun `una accion que no cambia nada no toca el estado`() = runTest(dispatcher) {
        val vm = MatchViewModel(now = { 0L })
        runCurrent()

        val antes = vm.state.value
        // Deshacer con el game en 0-0: no hay nada que deshacer.
        vm.dispatch(MatchAction.UndoPoint(Side.HOME))
        runCurrent()

        assertTrue("no debe crear un estado nuevo", antes === vm.state.value)
    }
}
