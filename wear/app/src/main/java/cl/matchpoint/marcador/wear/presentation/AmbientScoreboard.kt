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
 *    quemado. El marcador (lo que de verdad hay que leer de reojo entre punto y punto)
 *    va algo más claro que los nombres.
 *  - **Sin segundos** en el cronómetro: el sistema sólo despierta la app una vez por
 *    minuto, así que un contador de segundos estaría mintiendo — y son dos dígitos
 *    encendidos durante dos horas.
 *  - Contra el quemado, si el reloj lo pide ([burnInProtection]) todo se desplaza unos
 *    píxeles cada minuto, siguiendo [minuto] (el contador real de `onUpdateAmbient`, no
 *    los dígitos del texto).
 */
@Composable
fun AmbientScoreboard(
    match: MatchState,
    elapsed: String,
    burnInProtection: Boolean,
    minuto: Int,
    modifier: Modifier = Modifier,
) {
    // Desplazamiento anti-quemado: 9 posiciones en rejilla, una por minuto.
    val paso = if (burnInProtection) minuto.mod(9) else 4
    val dx = ((paso % 3) - 1) * 3
    val dy = ((paso / 3) - 1) * 3

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
                style = ESTILO_RELOJ,
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
            style = ESTILO_NOMBRE,
        )
        Text(
            text = "$sets   ${match.pointLabel(which)}",
            color = AMBIENT_CLARO,
            style = ESTILO_MARCADOR,
        )
    }
}

/**
 * Grises: en ambient no se usa blanco puro ni colores de marca.
 *
 * [AMBIENT_TENUE] baja a un gris más oscuro que antes — son los nombres, que uno ya sabe
 * de memoria. [AMBIENT_CLARO] es el marcador y **no** se baja más: en una cancha a pleno
 * sol hay que poder leerlo de un vistazo, y ahorrar batería a costa de eso sería cambiar
 * la app por una peor.
 */
private val AMBIENT_TENUE = Color(0xFF6E6E6E)
private val AMBIENT_CLARO = Color(0xFFCCCCCC)

/** Constantes y no `TextStyle(...)` dentro del `@Composable`: en ambient se recompone
 *  una vez por minuto, dos horas seguidas, y estos objetos son siempre los mismos. */
private val ESTILO_RELOJ = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Light)
private val ESTILO_NOMBRE = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Light)
private val ESTILO_MARCADOR = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientPreview() {
    MatchpointTheme {
        AmbientScoreboard(
            match = MatchState.new(),
            elapsed = "1:24",
            burnInProtection = true,
            minuto = 3,
        )
    }
}
