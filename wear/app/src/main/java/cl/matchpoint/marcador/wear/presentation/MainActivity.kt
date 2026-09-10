package cl.matchpoint.marcador.wear.presentation

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
 */
class MainActivity : ComponentActivity() {

    /** `null` fuera de ambient; con datos, el reloj dice si necesita anti-quemado. */
    private var ambiente by mutableStateOf<AmbientLifecycleObserver.AmbientDetails?>(null)
    private var minutoAmbient by mutableStateOf(0)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        lifecycle.addObserver(observadorAmbient)

        val repositorio = MatchRepository(applicationContext)
        setContent { WearApp(repositorio, ambiente, minutoAmbient) }
    }
}

/** Las dos pantallas de la app. */
private enum class Pantalla { PREPARAR, PARTIDO }

@Composable
fun WearApp(
    repositorio: MatchRepository,
    ambiente: AmbientLifecycleObserver.AmbientDetails?,
    minutoAmbient: Int,
) {
    val vm: MatchViewModel = viewModel(factory = MatchViewModel.factory(repositorio))
    val match by vm.state.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val cargado by vm.cargado.collectAsStateWithLifecycle()
    val (nombreLocal, nombreRival) = vm.nombres.collectAsStateWithLifecycle().value

    // La Ongoing Activity necesita poder notificar. En Wear 4+ es permiso de ejecución.
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { permiso.launch(Manifest.permission.POST_NOTIFICATIONS) }

    // El servicio en primer plano vive exactamente mientras dure un partido de verdad:
    // ni mientras se prepara, ni una vez terminado.
    val contexto = LocalContext.current
    val enJuego = match.empezado && !match.matchOver
    LaunchedEffect(enJuego) { MatchService.sincronizar(contexto, enJuego) }

    LaunchedEffect(ambiente) { vm.setAmbient(ambiente != null) }
    LaunchedEffect(minutoAmbient) { if (ambiente != null) vm.tickAmbient() }

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
                elapsed = elapsed,
                burnInProtection = ambiente.burnInProtectionRequired,
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
