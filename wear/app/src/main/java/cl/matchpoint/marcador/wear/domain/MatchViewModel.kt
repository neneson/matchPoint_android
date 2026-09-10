package cl.matchpoint.marcador.wear.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.matchpoint.marcador.wear.data.MatchRepository
import cl.matchpoint.marcador.wear.data.PartidoGuardado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado del partido para la UI. Toda la lógica vive en [reduce] (Kotlin puro y
 * probado); esto sólo la expone como [StateFlow], le pone el cronómetro encima y —
 * desde la Fase 4 — lo guarda en disco.
 *
 * El cronómetro se **congela** cuando termina el partido, igual que en la web
 * (`stoppedAtRef` en `partido.tsx`). La hora del reloj no la lleva este ViewModel:
 * en Wear la pinta `TimeText` del sistema.
 */
class MatchViewModel(
    private val repo: MatchRepository? = null,
    setup: MatchSetup = MatchSetup(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState.new(setup))
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _elapsed = MutableStateFlow("00:00")
    val elapsed: StateFlow<String> = _elapsed.asStateFlow()

    /** Falso hasta leer el disco: antes de eso no se sabe a qué pantalla abrir. */
    private val _cargado = MutableStateFlow(repo == null)
    val cargado: StateFlow<Boolean> = _cargado.asStateFlow()

    /** Últimos nombres usados, para no dictarlos en cada partido. */
    private val _nombres = MutableStateFlow("Yo" to "Rival")
    val nombres: StateFlow<Pair<String, String>> = _nombres.asStateFlow()

    private var startedAt: Long = now()
    private var stoppedAt: Long? = null
    private var ticker: Job? = null

    /** En ambient el sistema sólo despierta la app una vez por minuto: nada de tick de 1 s. */
    private var enAmbient = false

    init {
        viewModelScope.launch {
            // Un partido guardado se retoma tal cual, con su cronómetro corriendo desde
            // el instante original: la app pudo haber muerto en medio del segundo set.
            repo?.let { r ->
                r.nombres.first().let { _nombres.value = it }
                r.partido.first()?.let { guardado ->
                    _state.value = guardado.match
                    startedAt = guardado.startedAt
                    stoppedAt = guardado.stoppedAt
                }
            }
            _cargado.value = true
            arrancarCronometro()
            // Deja el partido en disco desde el arranque: el servicio en primer plano
            // lee de ahí y necesita encontrar algo antes del primer punto.
            guardar()
        }
    }

    fun dispatch(action: MatchAction) {
        _state.update { reduce(it, action) }
        // Al terminar el partido el tiempo queda clavado en el instante del último punto.
        if (_state.value.matchOver && stoppedAt == null) {
            stoppedAt = now()
            refrescarElapsed()
        }
        guardar()
    }

    /** Empieza un partido nuevo: reinicia marcador y cronómetro, y recuerda los nombres. */
    fun nuevoPartido(setup: MatchSetup) {
        _state.value = MatchState.new(setup)
        startedAt = now()
        stoppedAt = null
        _nombres.value = setup.local to setup.visitante
        arrancarCronometro()
        guardar()
        val repo = repo ?: return
        viewModelScope.launch { repo.guardarNombres(setup.local, setup.visitante) }
    }

    /**
     * Entrar o salir de ambient. Al entrar se refresca una vez y se para el tick; al
     * salir se vuelve a poner al día de golpe, porque el minuto que pasó no se contó.
     */
    fun setAmbient(ambient: Boolean) {
        if (enAmbient == ambient) return
        enAmbient = ambient
        if (ambient) {
            ticker?.cancel()
            refrescarElapsed()
        } else {
            arrancarCronometro()
        }
    }

    /** El sistema despierta la app una vez por minuto en ambient: es el único refresco. */
    fun tickAmbient() = refrescarElapsed()

    private fun guardar() {
        val repo = repo ?: return
        val instantanea = PartidoGuardado(_state.value, startedAt, stoppedAt)
        viewModelScope.launch { repo.guardar(instantanea) }
    }

    private fun arrancarCronometro() {
        ticker?.cancel()
        refrescarElapsed()
        if (enAmbient) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (stoppedAt != null) break
                refrescarElapsed()
            }
        }
    }

    private fun refrescarElapsed() {
        _elapsed.value = formatElapsed((stoppedAt ?: now()) - startedAt)
    }

    companion object {
        fun factory(repo: MatchRepository) = viewModelFactory {
            initializer { MatchViewModel(repo) }
        }
    }
}
