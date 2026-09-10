package cl.matchpoint.marcador.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import cl.matchpoint.marcador.wear.domain.MatchState
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointColors
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme

/**
 * El marcador en ambient: lo mismo, pero apagado. Un partido de tenis dura dos horas y
 * el reloj pasa la mayor parte de ese tiempo en este modo, así que aquí manda la batería:
 *
 *  - **Fondo negro puro** (`#000000`), no el gris azulado de la app: en OLED un píxel
 *    negro no consume. Es lo que el plan anotó como riesgo de la paleta actual.
 *  - **Sin rellenos de color**: las celdas de set pasan a texto, la pelota de saque a un
 *    punto. Menos píxeles encendidos.
 *  - **Texto fino y gris**, no blanco en negrita: menos corriente y menos riesgo de
 *    quemado.
 *  - Contra el quemado, si el reloj lo pide ([burnInProtection]) todo se desplaza unos
 *    píxeles cada minuto, siguiendo el minuto del cronómetro.
 */
@Composable
fun AmbientScoreboard(
    match: MatchState,
    elapsed: String,
    burnInProtection: Boolean,
    modifier: Modifier = Modifier,
) {
    // Desplazamiento anti-quemado: 4 posiciones que van rotando con los minutos.
    val paso = if (burnInProtection) elapsed.filter { it.isDigit() }.lastOrNull()?.digitToInt() ?: 0 else 0
    val dx = ((paso % 3) - 1) * 2
    val dy = ((paso / 3 % 3) - 1) * 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MatchpointColors.ambientBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .offset(x = dx.dp, y = dy.dp)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = elapsed,
                color = AMBIENT_TENUE,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Light),
            )
            FilaAmbient(match, Side.HOME)
            FilaAmbient(match, Side.AWAY)
        }
    }
}

@Composable
private fun FilaAmbient(match: MatchState, which: Side) {
    val jugador = match.of(which)
    val sets = (0 until match.rules.maxSets)
        .joinToString(" ") { (jugador.sets.getOrNull(it) ?: 0).toString() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (match.serving == which) "• ${jugador.name}" else jugador.name,
            color = AMBIENT_TENUE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Light),
        )
        Text(
            text = "$sets   ${match.pointLabel(which)}",
            color = AMBIENT_CLARO,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        )
    }
}

/** Grises: en ambient no se usa blanco puro ni colores de marca. */
private val AMBIENT_TENUE = Color(0xFF8A8A8A)
private val AMBIENT_CLARO = Color(0xFFD0D0D0)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientPreview() {
    MatchpointTheme {
        AmbientScoreboard(
            match = MatchState.new(),
            elapsed = "1:24:07",
            burnInProtection = true,
        )
    }
}
