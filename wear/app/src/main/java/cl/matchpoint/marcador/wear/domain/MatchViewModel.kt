package cl.matchpoint.marcador.wear.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.matchpoint.marcador.wear.data.MatchRepository
import cl.matchpoint.marcador.wear.data.PartidoGuardado
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado del partido para la UI. Toda la lógica vive en [reduce] (Kotlin puro y
 * probado); esto sólo la expone como [StateFlow], le pone el cronómetro encima y —
 * desde la Fase 4 — lo guarda en disco.
 *
 * El cronómetro se **congela** cuando termina el partido, igual que en la web
 * (`stoppedAtRef` en `partido.tsx`). La hora del reloj no la lleva este ViewModel:
 * en Wear la pinta `TimeText` del sistema.
 *
 * ## Batería (Fase 6)
 *
 * Un partido dura dos horas y el reloj lo aguanta con ~300 mAh, así que el cronómetro
 * es el consumidor más peligroso de la app: es lo único que despierta la CPU sin que el
 * usuario toque nada. Tres reglas, todas implementadas aquí:
 *
 *  1. **Nadie mirando, nadie contando.** [elapsed] se produce con
 *     `SharingStarted.WhileSubscribed`, y la UI lo consume con
 *     `collectAsStateWithLifecycle`. Cuando la actividad se para (el usuario vuelve al
 *     watch face) el productor muere solo. Antes el bucle de 1 s seguía corriendo dentro
 *     de `viewModelScope` mientras la actividad existiera: hasta 7.200 despertares por
 *     partido dibujando en una pantalla apagada.
 *  2. **Un despertar por cambio visible.** El `delay` se alinea al borde del segundo que
 *     se muestra, en vez de sumar 1.000 ms sobre el momento en que tocó ejecutarse; y al
 *     congelarse el partido el bucle termina en vez de seguir girando.
 *  3. **En ambient no hay bucle.** El sistema despierta la app una vez por minuto
 *     ([tickAmbient]); ese pulso es la única fuente de refresco, y el texto pasa a `H:MM`
 *     ([formatElapsedCorto]) porque unos segundos refrescados cada 60 s serían mentira.
 *
 * Los guardados en disco siguen la misma idea: se **conflan** ([pedirGuardar]) para que
 * una ráfaga de correcciones con la corona no se convierta en una ráfaga de reescrituras
 * de la flash, y sólo los momentos que no se pueden perder ([guardarYa]) escriben en el acto.
 *
 * El otro consumidor grande —la pantalla siempre encendida— no se decide aquí sino en
 * [siempreEncendido], que es un ajuste del usuario: ningún truco de código ahorra tanto
 * como no encender el OLED durante dos horas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModel(
    private val repo: MatchRepository? = null,
    setup: MatchSetup = MatchSetup(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState.new(setup))
    val state: StateFlow<MatchState> = _state.asStateFlow()

    /** Falso hasta leer el disco: antes de eso no se sabe a qué pantalla abrir. */
    private val _cargado = MutableStateFlow(repo == null)
    val cargado: StateFlow<Boolean> = _cargado.asStateFlow()

    /** Últimos nombres usados, para no dictarlos en cada partido. */
    private val _nombres = MutableStateFlow("Yo" to "Rival")
    val nombres: StateFlow<Pair<String, String>> = _nombres.asStateFlow()

    /**
     * Pantalla siempre encendida durante el partido. Es el interruptor de batería más
     * grande que tiene la app —ver [cl.matchpoint.marcador.wear.data.EstadoInicial]— y lo
     * decide el usuario en la pantalla de preparación.
     */
    private val _siempreEncendido = MutableStateFlow(true)
    val siempreEncendido: StateFlow<Boolean> = _siempreEncendido.asStateFlow()

    private var startedAt: Long = now()
    private var stoppedAt: Long? = null

    /** En ambient el sistema sólo despierta la app una vez por minuto: nada de tick de 1 s. */
    private val ambient = MutableStateFlow(false)

    /** Cada pulso es una llamada del sistema a `onUpdateAmbient` (una por minuto). */
    private val pulsoAmbient = MutableStateFlow(0)

    /**
     * Se incrementa cuando el cronómetro deja de valer: partido cargado del disco,
     * partido nuevo, partido terminado. Reinicia el productor de [elapsed] en el acto,
     * sin esperar al siguiente tick.
     */
    private val epoca = MutableStateFlow(0)

    /**
     * Texto del cronómetro. Sólo se produce mientras alguien lo mire — ver la nota de
     * batería de la clase.
     */
    val elapsed: StateFlow<String> = combine(ambient, epoca) { enAmbient, _ -> enAmbient }
        .flatMapLatest { enAmbient -> if (enAmbient) relojAmbient() else relojSegundos() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(GRACIA_MS), formatElapsed(0))

    /** Peticiones de guardado confladas: sólo interesa la última. */
    private val guardados = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch {
            // Un partido guardado se retoma tal cual, con su cronómetro corriendo desde
            // el instante original: la app pudo haber muerto en medio del segundo set.
            repo?.let { r ->
                val inicial = r.cargarInicial()
                _nombres.value = inicial.nombres
                _siempreEncendido.value = inicial.siempreEncendido
                inicial.partido?.let {
                    _state.value = it.match
                    startedAt = it.startedAt
                    stoppedAt = it.stoppedAt
                }
            }
            _cargado.value = true
            epoca.value++
            // Antes había aquí un `guardar()` "para que el servicio encuentre algo": ya no
            // hace falta (el servicio tolera el disco vacío) y era una escritura de flash
            // en cada arranque de la app, incluso sin jugar un punto.
        }

        if (repo != null) {
            viewModelScope.launch {
                guardados.collect {
                    repo.guardar(instantanea())
                    // Ventana de conflado: lo que llegue mientras tanto se junta en una
                    // sola escritura al salir del `delay`.
                    delay(MS_ENTRE_GUARDADOS)
                }
            }
        }
    }

    fun dispatch(action: MatchAction) {
        val antes = _state.value
        val despues = reduce(antes, action)
        // La acción puede no cambiar nada: partido terminado, o deshacer con el game en 0
        // (que sí construye un objeto nuevo, pero idéntico). Cortar aquí ahorra una
        // recomposición y, sobre todo, una escritura en disco por cada toque inútil.
        if (despues == antes) return
        _state.value = despues

        // Al terminar el partido el tiempo queda clavado en el instante del último punto.
        if (despues.matchOver && stoppedAt == null) {
            stoppedAt = now()
            epoca.value++
            guardarYa()
        } else {
            pedirGuardar()
        }
    }

    /** Empieza un partido nuevo: reinicia marcador y cronómetro, y recuerda los nombres. */
    fun nuevoPartido(setup: MatchSetup) {
        _state.value = MatchState.new(setup)
        startedAt = now()
        stoppedAt = null
        _nombres.value = setup.local to setup.visitante
        epoca.value++
        guardarYa()
        val repo = repo ?: return
        viewModelScope.launch { repo.guardarNombres(setup.local, setup.visitante) }
    }

    /**
     * Cambia la preferencia de pantalla siempre encendida. Se escribe en el acto: es una
     * sola clave booleana y el usuario acaba de tocarla, así que no hay ráfaga que conflar.
     */
    fun setSiempreEncendido(activo: Boolean) {
        if (_siempreEncendido.value == activo) return
        _siempreEncendido.value = activo
        val repo = repo ?: return
        viewModelScope.launch { repo.guardarSiempreEncendido(activo) }
    }

    /** Entrar o salir de ambient. Cambia la fuente de refresco del cronómetro. */
    fun setAmbient(enAmbient: Boolean) {
        ambient.value = enAmbient
    }

    /** El sistema despierta la app una vez por minuto en ambient: es el único refresco. */
    fun tickAmbient() {
        pulsoAmbient.value++
    }

    /**
     * Fuerza el volcado a disco. Se llama cuando la actividad se va a segundo plano: el
     * conflado puede tener hasta [MS_ENTRE_GUARDADOS] de retraso y el sistema puede matar
     * el proceso en cualquier momento después de eso.
     */
    fun guardarPendiente() = guardarYa()

    /** Cronómetro interactivo: un despertar por segundo visible, ni uno más. */
    private fun relojSegundos(): Flow<String> = flow {
        while (true) {
            emit(formatElapsed(transcurrido()))
            // Congelado: no hay nada más que emitir y el bucle termina de verdad.
            if (stoppedAt != null) break
            // Alineado al borde del segundo que se muestra: sin esto el reloj deriva y
            // acaba despertando justo entre dos cambios de dígito.
            val resto = transcurrido() % 1_000L
            delay(1_000L - if (resto < 0) 0L else resto)
        }
    }

    /** Cronómetro en ambient: sólo se refresca cuando el sistema despierta la app. */
    private fun relojAmbient(): Flow<String> =
        pulsoAmbient.map { formatElapsedCorto(transcurrido()) }

    private fun transcurrido(): Long = (stoppedAt ?: now()) - startedAt

    private fun instantanea() = PartidoGuardado(_state.value, startedAt, stoppedAt)

    private fun pedirGuardar() {
        if (repo == null) return
        guardados.tryEmit(Unit)
    }

    private fun guardarYa() {
        val repo = repo ?: return
        val instantanea = instantanea()
        viewModelScope.launch { repo.guardar(instantanea) }
    }

    companion object {
        /**
         * Cuánto sigue vivo el cronómetro tras perder al último observador. Absorbe los
         * cambios de configuración (que reinician la suscripción) sin reiniciar el flujo,
         * pero es corto: apagar la pantalla no debe dejar la CPU contando.
         */
        private const val GRACIA_MS = 1_000L

        /** Ventana de conflado de los guardados en disco. */
        private const val MS_ENTRE_GUARDADOS = 3_000L

        fun factory(repo: MatchRepository) = viewModelFactory {
            initializer { MatchViewModel(repo) }
        }
    }
}
