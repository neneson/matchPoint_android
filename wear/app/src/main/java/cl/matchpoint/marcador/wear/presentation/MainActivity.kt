package cl.matchpoint.marcador.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme

/**
 * Punto de entrada. `AppScaffold` es lo que pone el `TimeText` del sistema en el arco
 * superior — por eso el marcador ya no dibuja la hora a mano como la web.
 *
 * No se usa `ScreenScaffold`: ése existe para pantallas con scroll (lleva indicador de
 * posición y `ScrollInfoProvider`), y el marcador cabe entero sin desplazarse.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    MatchpointTheme {
        AppScaffold {
            ScoreboardScreen()
        }
    }
}
