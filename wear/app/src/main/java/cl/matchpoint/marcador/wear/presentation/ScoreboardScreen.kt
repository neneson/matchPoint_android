@file:OptIn(ExperimentalComposeUiApi::class)

package cl.matchpoint.marcador.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import cl.matchpoint.marcador.wear.domain.MatchAction
import cl.matchpoint.marcador.wear.domain.MatchState
import cl.matchpoint.marcador.wear.domain.MatchViewModel
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointColors
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * El marcador. Traducción a Compose del cuadrante superior + los dos botones de
 * puntos de la web (`partido.tsx:318-395`), con cuatro cambios que impone el reloj:
 *
 *  - **Nada en px.** El diseño web era 320×320 px fijos; aquí todo sale de
 *    [BoxWithConstraints] en fracciones de la pantalla, que mide ~227 dp.
 *  - **La hora la pinta el sistema** (`TimeText` del `AppScaffold`), así que la fila 0
 *    se queda sólo con el cronómetro del partido.
 *  - **Pantalla redonda**: el tablero se mete hacia adentro lo que le come el bisel a
 *    su altura (ver [insetCircular]); los botones de puntos dejan margen abajo.
 *  - **Deshacer es long-press** (en la web era clic derecho) y también corona.
 */
@Composable
fun ScoreboardScreen(
    vm: MatchViewModel = viewModel(),
    onNuevoPartido: () -> Unit = {},
) {
    val match by vm.state.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val redonda = LocalConfiguration.current.isScreenRound

    var dialogoVisto by remember { mutableStateOf(false) }
    LaunchedEffect(match.matchOver) { if (!match.matchOver) dialogoVisto = false }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MatchpointColors.background)
            .rotaryCorrigePuntos(match, haptics) { vm.dispatch(it) },
    ) {
        val radio = minOf(maxWidth, maxHeight) / 2

        // El arco superior es de `TimeText`; el tablero empieza debajo.
        val margenSuperior = if (redonda) maxHeight * 0.15f else 6.dp
        val margenInferior = if (redonda) maxHeight * 0.09f else 6.dp
        val altoTablero = maxHeight * 0.34f

        // Cuánto se mete el bisel a la altura del borde superior del tablero, que es
        // su punto más estrecho por estar más lejos del centro.
        val insetTablero =
            if (redonda) insetCircular(radio, radio - margenSuperior) + 2.dp else 4.dp

        // Los botones NO se insetan como el tablero. Insetarlos por su borde inferior
        // costaría 60 dp de los 227 y dejaría dos tiras angostas; en vez de eso van casi
        // de borde a borde y lo que sobresale se redondea con un radio del tamaño de la
        // curva del bisel, en las dos esquinas de abajo. Proporcional a la pantalla para
        // que valga igual en un reloj de 192 dp que en uno de 227.
        val insetBotones = 6.dp
        val esquinaBisel = if (redonda) maxHeight * 0.20f else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(margenSuperior))

            Tablero(
                match = match,
                elapsed = elapsed,
                modifier = Modifier
                    .padding(horizontal = insetTablero)
                    .fillMaxWidth()
                    .height(altoTablero),
            )

            BotonesDePunto(
                match = match,
                esquinaBisel = esquinaBisel,
                modifier = Modifier
                    .padding(horizontal = insetBotones, vertical = 4.dp)
                    .fillMaxWidth()
                    .weight(1f),
                onPunto = { side ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.dispatch(MatchAction.Point(side))
                },
                onDeshacer = { side ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.dispatch(MatchAction.UndoPoint(side))
                },
            )

            Spacer(Modifier.height(margenInferior))
        }
    }

    AlertDialog(
        visible = match.matchOver && !dialogoVisto,
        onDismissRequest = { dialogoVisto = true },
        // Equivale al enlace "Nuevo match" de la web: vuelve a la preparación, donde se
        // eligen nombres y saque (Fase 5).
        edgeButton = {
            EdgeButton(onClick = onNuevoPartido) { Text("Nuevo") }
        },
        title = {
            val ganador = match.winner?.let { match.of(it).name } ?: ""
            Text(
                text = "$ganador ganó el partido",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "${match.homeSetsWon} - ${match.awaySetsWon} en sets · $elapsed",
                textAlign = TextAlign.Center,
                color = MatchpointColors.mutedForeground,
            )
        },
    )
}

/** Fila 0 (cronómetro) + fila del local + fila del visitante. */
@Composable
private fun Tablero(match: MatchState, elapsed: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Fila 0: en la web eran cronómetro | vacío | hora. La hora ahora la pinta
        // `TimeText`, así que queda el cronómetro centrado sobre fondo azul marino.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .clip(RoundedCornerShape(4.dp))
                .background(MatchpointColors.navy),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = elapsed,
                color = MatchpointColors.mutedForeground,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        FilaJugador(match, Side.HOME, Modifier.weight(1f))
        FilaJugador(match, Side.AWAY, Modifier.weight(1f))
    }
}

/** Nombre · un recuadro por set · pelota de saque. Todo por pesos, sin anchos fijos. */
@Composable
private fun FilaJugador(match: MatchState, which: Side, modifier: Modifier = Modifier) {
    val jugador = match.of(which)
    val rival = match.of(which.other)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = jugador.name,
            modifier = Modifier.weight(3f),
            color = MatchpointColors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )

        repeat(match.rules.maxSets) { i ->
            val propios = jugador.sets.getOrNull(i) ?: 0
            val ajenos = rival.sets.getOrNull(i) ?: 0
            val enJuego = jugador.currentSetIndex == i
            CeldaSet(
                games = propios,
                enJuego = enJuego,
                ganado = !enJuego && propios > ajenos,
                modifier = Modifier.weight(1f),
            )
        }

        // Columnas negras de la web; aquí sólo sirven para la pelota de saque.
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(MatchpointColors.ambientBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (match.serving == which) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MatchpointColors.ball),
                )
            }
        }
    }
}

/** Azul eléctrico si el set está en juego, azul marino si terminó; ganador en ámbar. */
@Composable
private fun CeldaSet(games: Int, enJuego: Boolean, ganado: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(if (enJuego) MatchpointColors.setActive else MatchpointColors.setDone),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = games.toString(),
            color = if (ganado) MatchpointColors.accent else MatchpointColors.mutedForeground,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Los dos botones grandes: toque = punto, mantener = deshacer. */
@Composable
private fun BotonesDePunto(
    match: MatchState,
    esquinaBisel: Dp,
    modifier: Modifier = Modifier,
    onPunto: (Side) -> Unit,
    onDeshacer: (Side) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Side.entries.forEach { side ->
            // La esquina de abajo que mira al borde de la pantalla se redondea fuerte:
            // es la que el bisel recorta en un reloj redondo.
            val forma = if (side == Side.HOME) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = esquinaBisel)
            } else {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = esquinaBisel, bottomStart = 20.dp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(forma)
                    .background(MatchpointColors.card)
                    .combinedClickable(
                        enabled = !match.matchOver,
                        onClick = { onPunto(side) },
                        onLongClick = { onDeshacer(side) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = match.pointLabel(side),
                    color = MatchpointColors.cardForeground,
                    maxLines = 1,
                    style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * Corona: corrige puntos sin tener que apuntar a un botón de 1 cm. Girar hacia
 * adelante suma un punto al jugador que saca, hacia atrás se lo quita. Los eventos
 * llegan en píxeles, así que se acumulan hasta un umbral para que un giro suave no
 * dispare una ráfaga de puntos.
 */
@Composable
private fun Modifier.rotaryCorrigePuntos(
    match: MatchState,
    haptics: HapticFeedback,
    dispatch: (MatchAction) -> Unit,
): Modifier {
    val foco = remember { FocusRequester() }
    var acumulado by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { foco.requestFocus() }

    return this
        .onRotaryScrollEvent { evento ->
            if (match.matchOver) return@onRotaryScrollEvent false
            acumulado += evento.verticalScrollPixels
            if (abs(acumulado) >= UMBRAL_CORONA) {
                // Ojo con el signo: `verticalScrollPixels` viene invertido respecto de
                // `AXIS_SCROLL` (girar la corona hacia adelante llega aquí en negativo,
                // igual que un scroll que avanza). Comprobado en el emulador con
                // `adb shell input rotaryencoder scroll --axis SCROLL,±3`.
                val suma = acumulado < 0
                acumulado = 0f
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                dispatch(
                    if (suma) MatchAction.Point(match.serving)
                    else MatchAction.UndoPoint(match.serving)
                )
            }
            true
        }
        .focusRequester(foco)
        .focusable()
}

private const val UMBRAL_CORONA = 40f

/**
 * Cuánto se come el bisel de un borde horizontal que está a [distanciaAlCentro] del
 * centro de una pantalla redonda de radio [radio]: `r - sqrt(r² - y²)`.
 */
private fun insetCircular(radio: Dp, distanciaAlCentro: Dp): Dp {
    val r = radio.value
    val y = distanciaAlCentro.value.coerceIn(0f, r)
    return (r - sqrt(r * r - y * y)).dp
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun ScoreboardPreviewRound() {
    MatchpointTheme { ScoreboardScreen() }
}

@Preview(device = WearDevices.SQUARE, showSystemUi = true)
@Composable
private fun ScoreboardPreviewSquare() {
    MatchpointTheme { ScoreboardScreen() }
}
