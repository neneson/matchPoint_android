package cl.matchpoint.marcador.wear.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * ¿Está el reloj en modo de ahorro de energía?
 *
 * Wear lo enciende solo cuando la batería baja, y es exactamente el momento en que la app
 * no puede permitirse tener la pantalla encendida dos horas: con esto el always-on se cae
 * solo y el partido sigue guardándose igual. Es la única concesión automática que hace la
 * app — el resto de la batería la decide el usuario en la pantalla de preparación.
 *
 * El receptor se registra **sólo mientras haya partido** ([vigilar]): un `BroadcastReceiver`
 * vivo todo el tiempo sería justo el tipo de gasto que se está tratando de evitar. El
 * `IntentFilter` es de un broadcast protegido del sistema, así que va `NOT_EXPORTED`.
 */
@Composable
fun ahorroDeEnergia(vigilar: Boolean): Boolean {
    val contexto = LocalContext.current
    var activo by remember { mutableStateOf(false) }

    DisposableEffect(vigilar, contexto) {
        if (!vigilar) {
            activo = false
            return@DisposableEffect onDispose { }
        }

        val energia = contexto.getSystemService<PowerManager>()
        activo = energia?.isPowerSaveMode == true

        val receptor = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                activo = energia?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            contexto,
            receptor,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { contexto.unregisterReceiver(receptor) }
    }

    return activo
}
