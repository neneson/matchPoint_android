package cl.matchpoint.marcador.wear.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado del partido para la UI. Toda la lógica vive en [reduce] (Kotlin puro y
 * probado); esto sólo la expone como [StateFlow] y le pone el cronómetro encima,
 * que es lo único que necesita un reloj de verdad.
 *
 * El cronómetro se **congela** cuando termina el partido, igual que en la web
 * (`stoppedAtRef` en `partido.tsx`). La hora del reloj no la lleva este ViewModel:
 * en Wear la pinta `TimeText` del sistema.
 */
class MatchViewModel(
    setup: MatchSetup = MatchSetup(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState.new(setup))
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _elapsed = MutableStateFlow("00:00")
    val elapsed: StateFlow<String> = _elapsed.asStateFlow()

    private var startedAt: Long = now()
    private var stoppedAt: Long? = null
    private var ticker: Job? = null

    init {
        arrancarCronometro()
    }

    fun dispatch(action: MatchAction) {
        _state.update { reduce(it, action) }
        // Al terminar el partido el tiempo queda clavado en el instante del último punto.
        if (_state.value.matchOver && stoppedAt == null) {
            stoppedAt = now()
            refrescarElapsed()
        }
    }

    /** Empieza un partido nuevo: reinicia marcador y cronómetro. */
    fun nuevoPartido(setup: MatchSetup) {
        _state.value = MatchState.new(setup)
        startedAt = now()
        stoppedAt = null
        arrancarCronometro()
    }

    private fun arrancarCronometro() {
        ticker?.cancel()
        refrescarElapsed()
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
}
