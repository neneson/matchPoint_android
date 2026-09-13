package cl.matchpoint.marcador.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import cl.matchpoint.marcador.wear.data.MatchRepository
import cl.matchpoint.marcador.wear.domain.MatchViewModel
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme
import cl.matchpoint.marcador.wear.service.MatchService

/**
 * Punto de entrada. `AppScaffold` es lo que pone el `TimeText` del sistema en el arco
 * superior — por eso el marcador ya no dibuja la hora a mano como la web.
 *
 * No se usa `ScreenScaffold`: ése existe para pantallas con scroll (lleva indicador de
 * posición y `ScrollInfoProvider`), y el marcador cabe entero sin desplazarse.
 *
 * Desde la Fase 4 la actividad además es *always-on*: [AmbientLifecycleObserver] la
 * mantiene visible cuando el reloj se apaga, y la pantalla cambia a [AmbientScoreboard].
 *
 * ## Batería (Fase 6)
 *
 * El always-on es, de lejos, lo que más gasta: la pantalla se queda encendida en vez de
 * apagarse. Por eso ya **no** se registra en `onCreate` para siempre, sino sólo mientras
 * hay un partido de verdad en juego ([alwaysOn]). Antes, quedarse en la pantalla de
 * preparación —o dejar la app abierta al terminar el partido— mantenía el reloj
 * encendido indefinidamente.
 *
 * Sobre esa base hay otras dos condiciones, las dos en [WearApp]: el usuario puede
 * apagarlo del todo desde la pantalla de preparación
 * ([cl.matchpoint.marcador.wear.domain.MatchViewModel.siempreEncendido]) y el modo de
 * ahorro de energía del reloj lo desactiva solo ([ahorroDeEnergia]).
 */
class MainActivity : ComponentActivity() {

    /** `null` fuera de ambient; con datos, el reloj dice si necesita anti-quemado. */
    private var ambiente by mutableStateOf<AmbientLifecycleObserver.AmbientDetails?>(null)
    private var minutoAmbient by mutableIntStateOf(0)

    private val observadorAmbient = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(details: AmbientLifecycleObserver.AmbientDetails) {
                ambiente = details
            }

            // El sistema llama esto una vez por minuto: es el único refresco que hay.
            override fun onUpdateAmbient() {
                minutoAmbient++
            }

            override fun onExitAmbient() {
                ambiente = null
            }
        },
    )

    private var alwaysOnActivo = false

    /**
     * Enciende o apaga el modo always-on. Registrar el observador tarde es válido: el
     * `Lifecycle` le reproduce los eventos hasta el estado actual.
     */
    private fun alwaysOn(activo: Boolean) {
        if (activo == alwaysOnActivo) return
        alwaysOnActivo = activo
        if (activo) {
            lifecycle.addObserver(observadorAmbient)
        } else {
            lifecycle.removeObserver(observadorAmbient)
            // Si el partido termina con el reloj ya en ambient hay que volver a la
            // pantalla normal; a partir de aquí el reloj se apaga como cualquier app.
            ambiente = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val repositorio = MatchRepository(applicationContext)
        setContent { WearApp(repositorio, ambiente, minutoAmbient, ::alwaysOn) }
    }
}

/** Las dos pantallas de la app. */
private enum class Pantalla { PREPARAR, PARTIDO }

@Composable
fun WearApp(
    repositorio: MatchRepository,
    ambiente: AmbientLifecycleObserver.AmbientDetails?,
    minutoAmbient: Int,
    alwaysOn: (Boolean) -> Unit = {},
) {
    val vm: MatchViewModel = viewModel(factory = MatchViewModel.factory(repositorio))
    val match by vm.state.collectAsStateWithLifecycle()
    val cargado by vm.cargado.collectAsStateWithLifecycle()
    val (nombreLocal, nombreRival) = vm.nombres.collectAsStateWithLifecycle().value

    val siempreEncendido by vm.siempreEncendido.collectAsStateWithLifecycle()

    val contexto = LocalContext.current
    val enJuego = match.empezado && !match.matchOver

    // Las tres condiciones del always-on: que haya partido, que el usuario lo quiera y que
    // el reloj no esté ya racionando batería.
    val ahorroActivo = ahorroDeEnergia(vigilar = enJuego)
    val pantallaSiempre = enJuego && siempreEncendido && !ahorroActivo

    // La Ongoing Activity necesita poder notificar. En Wear 4+ es permiso de ejecución.
    // Se pide una sola vez y sólo cuando hace falta de verdad — o sea, al empezar a jugar,
    // no al abrir la app: el diálogo despierta pantalla y usuario para nada.
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var permisoPedido by remember { mutableStateOf(false) }
    LaunchedEffect(enJuego) {
        val concedido = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (enJuego && !concedido && !permisoPedido) {
            permisoPedido = true
            permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // El servicio en primer plano vive exactamente mientras dure un partido de verdad:
    // ni mientras se prepara, ni una vez terminado.
    LaunchedEffect(enJuego) { MatchService.sincronizar(contexto, enJuego) }
    LaunchedEffect(pantallaSiempre) { alwaysOn(pantallaSiempre) }
    DisposableEffect(Unit) {
        onDispose {
            alwaysOn(false)
            MatchService.uiVisible(false)
        }
    }

    LaunchedEffect(ambiente) { vm.setAmbient(ambiente != null) }
    LaunchedEffect(minutoAmbient) { if (ambiente != null) vm.tickAmbient() }

    // Dos cosas cuelgan de estar o no en pantalla:
    //  - Los guardados se conflan para no castigar la flash; al irse a segundo plano hay
    //    que volcar lo pendiente, porque a partir de ahí el sistema puede matar el proceso.
    //  - El servicio sólo trabaja cuando la app NO está delante: la Ongoing Activity se ve
    //    en el watch face, y mientras el marcador esté a la vista sería un dibujo doble.
    val duenoCicloVida = LocalLifecycleOwner.current
    DisposableEffect(duenoCicloVida) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> MatchService.uiVisible(true)
                Lifecycle.Event.ON_STOP -> {
                    MatchService.uiVisible(false)
                    vm.guardarPendiente()
                }
                else -> Unit
            }
        }
        duenoCicloVida.lifecycle.addObserver(observador)
        onDispose {
            duenoCicloVida.lifecycle.removeObserver(observador)
            MatchService.uiVisible(false)
        }
    }

    // Sólo dos pantallas y sin `SwipeDismissableNavHost` a propósito: el gesto de volver
    // atrás en medio de un partido saldría del marcador sin querer.
    var pantalla by remember { mutableStateOf<Pantalla?>(null) }
    LaunchedEffect(cargado) {
        // Si había un partido a medias se retoma; si no, se prepara uno nuevo.
        if (cargado && pantalla == null) {
            pantalla = if (match.empezado && !match.matchOver) Pantalla.PARTIDO else Pantalla.PREPARAR
        }
    }

    MatchpointTheme {
        if (ambiente != null) {
            // En ambient no va `AppScaffold`: su `TimeText` es de la app activa y aquí
            // manda el reloj del sistema. Pantalla propia, negra y sin rellenos.
            AmbientScoreboard(
                match = match,
                // `CREATED` y no el `STARTED` de siempre: en ambient el sistema puede
                // dejar la actividad pausada aunque la pantalla siga dibujándose, y con el
                // umbral normal el cronómetro quedaría congelado en la hora en que se
                // apagó. Aquí no cuesta nada: en ambient el flujo sólo emite cuando llega
                // el pulso del sistema, una vez por minuto.
                elapsed = vm.elapsed
                    .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
                    .value,
                burnInProtection = ambiente.burnInProtectionRequired,
                minuto = minutoAmbient,
            )
        } else {
            AppScaffold {
                when (pantalla) {
                    // Antes de leer el disco no se dibuja nada: abrir en la pantalla
                    // equivocada y saltar a la otra se ve como un parpadeo.
                    null -> Unit
                    Pantalla.PREPARAR -> PrepararScreen(
                        nombreLocal = nombreLocal,
                        nombreRival = nombreRival,
                        siempreEncendido = siempreEncendido,
                        onSiempreEncendido = vm::setSiempreEncendido,
                        onEmpezar = { setup ->
                            vm.nuevoPartido(setup)
                            pantalla = Pantalla.PARTIDO
                        },
                    )
                    Pantalla.PARTIDO -> ScoreboardScreen(
                        vm = vm,
                        onNuevoPartido = { pantalla = Pantalla.PREPARAR },
                    )
                }
            }
        }
    }
}
