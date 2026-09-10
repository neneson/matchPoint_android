package cl.matchpoint.marcador.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import cl.matchpoint.marcador.wear.R
import cl.matchpoint.marcador.wear.data.MatchRepository
import cl.matchpoint.marcador.wear.domain.MatchState
import cl.matchpoint.marcador.wear.domain.Side
import cl.matchpoint.marcador.wear.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que publica el partido como **Ongoing Activity**: mientras
 * corre, el marcador queda visible en el watch face y el sistema no mata la app aunque
 * el reloj apague la pantalla.
 *
 * No recibe el marcador por el intent: lee el mismo [MatchRepository] que escribe el
 * ViewModel, así no hay dos fuentes de verdad ni binding que mantener.
 */
class MatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: MatchRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = MatchRepository(this)
        crearCanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Arrancar con algo ya dibujado: `startForeground` tiene que llamarse enseguida.
        startForeground(ID_NOTIFICACION, construirNotificacion("Partido en curso", null))

        repo.partido
            .onEach { guardado ->
                val match = guardado?.match
                when {
                    // Todavía no hay nada escrito en disco (primer arranque): se deja la
                    // notificación de arranque. Ojo: parar aquí mataba el servicio antes
                    // de que el ViewModel alcanzara a guardar el primer punto.
                    match == null -> Unit
                    // Terminado: la Ongoing Activity deja de tener sentido.
                    match.matchOver -> detener()
                    else -> notificar(match)
                }
            }
            .launchIn(scope)

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun detener() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notificar(match: MatchState) {
        val sets = (0 until match.rules.maxSets).joinToString(" ") { i ->
            "${match.home.sets.getOrNull(i) ?: 0}-${match.away.sets.getOrNull(i) ?: 0}"
        }
        val puntos = "${match.pointLabel(Side.HOME)} - ${match.pointLabel(Side.AWAY)}"
        val notificacion = construirNotificacion(puntos, sets)
        getSystemService<NotificationManager>()?.notify(ID_NOTIFICACION, notificacion)
    }

    private fun construirNotificacion(titulo: String, detalle: String?): Notification {
        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_partido)
            .setContentTitle(titulo)
            .setContentText(detalle ?: getString(R.string.app_name))
            .setContentIntent(abrir)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Esto es lo que convierte una notificación normal en un ítem del watch face.
        OngoingActivity.Builder(this, ID_NOTIFICACION, builder)
            .setStaticIcon(R.drawable.ic_partido)
            .setTouchIntent(abrir)
            .setStatus(Status.Builder().addTemplate(titulo).build())
            .build()
            .apply(this)

        return builder.build()
    }

    private fun crearCanal() {
        val canal = NotificationChannel(
            CANAL,
            "Partido en curso",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(false) }
        getSystemService<NotificationManager>()?.createNotificationChannel(canal)
    }

    companion object {
        private const val CANAL = "partido"
        private const val ID_NOTIFICACION = 1

        /** Arranca el servicio si hay partido en juego; lo para si ya terminó. */
        fun sincronizar(context: Context, enJuego: Boolean) {
            val intent = Intent(context, MatchService::class.java)
            if (enJuego) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
