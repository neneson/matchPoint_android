package cl.matchpoint.marcador.wear.presentation

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import cl.matchpoint.marcador.wear.domain.MatchSetup
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointColors
import cl.matchpoint.marcador.wear.presentation.theme.MatchpointTheme
import kotlin.random.Random

/**
 * Preparar el partido, versión reloj de la pantalla `preparar` de la web.
 *
 * Es la **opción 1 del plan para la Fase 5**: la app es *standalone*, sin login. Escribir
 * un email y una contraseña en una pantalla de 2,5 cm es mal negocio, así que aquí no hay
 * identidad: sólo dos nombres, y se dictan. El nombre entra por
 * `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, que en Wear lo atiende el panel de entrada
 * del sistema — el usuario elige voz, teclado o escritura a mano.
 *
 * Los nombres se recuerdan de un partido al siguiente: dictar cuesta, y normalmente se
 * juega contra el mismo rival.
 *
 * Batería (Fase 6): aquí vive además el único ajuste de la app, [siempreEncendido]. Es el
 * interruptor más caro que existe —mantener el OLED encendido las dos horas del partido
 * contra dejar que el reloj se apague y despierte al levantar la muñeca— y por eso se
 * pregunta antes de empezar, cuando el usuario todavía sabe si va a jugar un set suelto o
 * un partido largo con la batería a medias.
 */
@Composable
fun PrepararScreen(
    nombreLocal: String,
    nombreRival: String,
    siempreEncendido: Boolean = true,
    onSiempreEncendido: (Boolean) -> Unit = {},
    onEmpezar: (MatchSetup) -> Unit,
) {
    val contexto = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var local by remember(nombreLocal) { mutableStateOf(nombreLocal) }
    var rival by remember(nombreRival) { mutableStateOf(nombreRival) }
    var saca by remember { mutableStateOf(Side.HOME) }
    var sinDictado by remember { mutableStateOf(false) }

    // A quién le toca el nombre que vuelva del panel de entrada.
    var dictandoPara by remember { mutableStateOf(Side.HOME) }

    val dictado = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { resultado ->
        val texto = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@rememberLauncherForActivityResult
        if (dictandoPara == Side.HOME) local = texto else rival = texto
    }

    fun pedirNombre(para: Side, titulo: String) {
        dictandoPara = para
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, titulo)
        // Un reloj sin panel de entrada (algunos sin Play Services) no debe cerrar la app.
        sinDictado = runCatching { dictado.launch(intent) }.isFailure
    }

    val estado = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(
            state = estado,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { ListHeader { Text("Nuevo partido") } }

            item {
                FilaNombre("Local", local) { pedirNombre(Side.HOME, "¿Cómo te llamas?") }
            }
            item {
                FilaNombre("Visitante", rival) { pedirNombre(Side.AWAY, "¿Contra quién juegas?") }
            }

            item {
                // Sorteo: el reloj hace de moneda al aire. Un toque en la fila lo alterna
                // a mano, por si la moneda ya se lanzó de verdad.
                Button(
                    onClick = {
                        saca = if (saca == Side.HOME) Side.AWAY else Side.HOME
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(
                        text = "Saca: ${if (saca == Side.HOME) local else rival}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 13.sp),
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        saca = if (Random.nextBoolean()) Side.HOME else Side.AWAY
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("Sortear saque", style = TextStyle(fontSize = 13.sp))
                }
            }

            item {
                // El texto dice la consecuencia, no el nombre técnico del modo: "ambient"
                // no le dice nada a nadie y "always-on" tampoco.
                Button(
                    onClick = {
                        onSiempreEncendido(!siempreEncendido)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Column2(
                        etiqueta = "Pantalla",
                        valor = if (siempreEncendido) "Siempre encendida" else "Se apaga (ahorra)",
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        onEmpezar(MatchSetup(local = local, visitante = rival, saca = saca))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Empezar", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }

            if (sinDictado) {
                item {
                    Text(
                        text = "Este reloj no tiene panel de dictado; se usan los nombres guardados.",
                        color = MatchpointColors.mutedForeground,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** Etiqueta + nombre actual; el toque abre el panel de entrada del sistema. */
@Composable
private fun FilaNombre(etiqueta: String, valor: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Column2(etiqueta, valor)
    }
}

@Composable
private fun Column2(etiqueta: String, valor: String) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            color = MatchpointColors.mutedForeground,
            style = TextStyle(fontSize = 10.sp),
        )
        Text(
            text = valor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PrepararPreview() {
    MatchpointTheme {
        PrepararScreen(nombreLocal = "Rene", nombreRival = "Nacho", onEmpezar = {})
    }
}
