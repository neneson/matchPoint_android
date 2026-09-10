package cl.matchpoint.marcador.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import cl.matchpoint.marcador.wear.domain.MatchAction
import cl.matchpoint.marcador.wear.domain.MatchViewModel
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointColors
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme
import kotlin.math.sqrt

/**
 * Pantalla de diagnóstico de la Fase 1. Muestra las medidas reales del reloj, que
 * son la entrada de la Fase 3:
 *
 * - El diseño web está fijado en **320x320 px**; los relojes actuales miden 384 o
 *   454 px, pero eso da ~192-227 **dp**. Hay que maquetar en dp, no en px.
 * - En pantalla redonda sólo se puede confiar en el **cuadrado inscrito**
 *   (lado = diámetro / raíz de 2). El resto lo corta el bisel, y ahí es donde
 *   hoy caen las esquinas de los dos botones de puntos.
 *
 * Desde la Fase 2 también hace de prueba de humo del [MatchViewModel]: si el
 * cronómetro avanza y el marcador reacciona al toque, la lógica portada está viva
 * dentro del reloj y no sólo en los tests de la JVM.
 */
@Composable
fun DeviceCheckScreen(vm: MatchViewModel = viewModel()) {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    val isRound = config.isScreenRound
    val safeSquareDp = (minOf(widthDp, heightDp) / sqrt(2f)).toInt()

    val match by vm.state.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MatchpointColors.background),
        contentAlignment = Alignment.Center,
    ) {
        // Cuadrado inscrito: el área que sí se ve completa en un reloj redondo.
        Box(
            modifier = Modifier
                .size(safeSquareDp.dp)
                .background(MatchpointColors.navy),
        )

        Column(
            modifier = Modifier
                .size(safeSquareDp.dp)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Matchpoint Wear",
                style = MaterialTheme.typography.titleMedium,
                color = MatchpointColors.foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Dato("Pantalla", if (isRound) "redonda" else "cuadrada")
            Dato("Tamaño", "$widthDp x $heightDp dp")
            Dato("Área segura", "$safeSquareDp dp")
            Dato("Diseño web", "320 x 320 px")
            Dato("Cronómetro", elapsed)
            Dato(
                "Marcador",
                "${match.pointLabel(Side.HOME)} - ${match.pointLabel(Side.AWAY)}",
            )
        }

        // Mitad izquierda / derecha: punto para local / visitante. Provisional,
        // sólo para comprobar que el reducer responde en el reloj (la UI es Fase 3).
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { vm.dispatch(MatchAction.Point(Side.HOME)) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { vm.dispatch(MatchAction.Point(Side.AWAY)) },
            )
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Text(
        text = "$etiqueta: $valor",
        style = MaterialTheme.typography.bodySmall,
        color = MatchpointColors.mutedForeground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun DeviceCheckPreviewRound() {
    MatchpointTheme { DeviceCheckScreen() }
}

@Preview(device = WearDevices.SQUARE, showSystemUi = true)
@Composable
private fun DeviceCheckPreviewSquare() {
    MatchpointTheme { DeviceCheckScreen() }
}
