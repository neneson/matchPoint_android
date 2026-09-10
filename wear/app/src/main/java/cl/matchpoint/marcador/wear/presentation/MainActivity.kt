package cl.matchpoint.marcador.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme

/**
 * Fase 1: el módulo Wear existe, compila y arranca. La pantalla que muestra es un
 * diagnóstico del reloj (ver [DeviceCheckScreen]); el marcador real llega en la
 * Fase 3, sobre la lógica portada a Kotlin en la Fase 2.
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
            DeviceCheckScreen()
        }
    }
}
