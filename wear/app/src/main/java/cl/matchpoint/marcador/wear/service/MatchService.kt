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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Servicio en primer plano que publica el partido como **Ongoing Activity**: mientras
 * corre, el marcador queda visible en el watch face y el sistema no mata la app aunque
 * el reloj apague la pantalla.
 *
 * No recibe el marcador por el intent: lee el mismo [MatchRepository] que escribe el
 * ViewModel, así no hay dos fuentes de verdad ni binding que mantener.
 *
 * ## Batería (Fase 6)
 *
 * Una notificación en Wear no es un dibujo barato. Cuatro correcciones, por orden de lo
 * que costaban:
 *
 *  1. **`setLocalOnly(true)`.** Sin esto, cada actualización del marcador se puenteaba por
 *     Bluetooth al teléfono para mostrar la misma notificación allá. Un partido son unos
 *     200 puntos: 200 encendidos de radio y 200 notificaciones que nadie pidió.
 *  2. **Canal de importancia baja + silencioso + `onlyAlertOnce`.** El canal era
 *     `IMPORTANCE_DEFAULT`, o sea con derecho a vibrar y sonar en cada punto.
 *  3. **Sólo se notifica si el texto cambió.** [Texto] se compara antes de reconstruir
 *     nada; un guardado que no mueve el marcador (o que sólo toca los nombres) ya no
 *     dispara un redibujado del watch face.
 *  4. **El `PendingIntent` y el `NotificationManager` se cachean**, y el JSON se parsea en
 *     [Dispatchers.Default] (lo hace el repositorio), nunca en el hilo principal.
 *  5. **Con la app en pantalla el servicio no hace nada.** La Ongoing Activity sólo se ve
 *     en el watch face, o sea justo cuando la app *no* está delante; mientras el marcador
 *     esté a la vista —ambient incluido— el servicio ni lee el disco ni notifica. Ver
 *     [uiVisible]: es el otro consumidor de cada punto, y durante el partido el usuario
 *     tiene la app abierta casi todo el tiempo.
 *
 * Además, la notificación lleva una acción **Cerrar** para poder soltar el servicio desde
 * el watch face, sin abrir la app; y un servicio resucitado por `START_STICKY` sobre un
 * partido rancio se apaga solo ([HORAS_MAXIMAS]) en vez de quedarse encendido para siempre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: MatchRepository

    private val notificador by lazy { getSystemService<NotificationManager>() }

    /** Se reusa en cada notificación: crearlo cuesta una consulta al PackageManager. */
    private val intentAbrir: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val intentCerrar: PendingIntent by lazy {
        PendingIntent.getService(
            this,
            1,
            Intent(this, MatchService::class.java).setAction(ACCION_DETENER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Lo último publicado, para no repetir notificaciones idénticas. */
    private var ultimo: Texto? = null

    private var observando = false

    /** Lo que se muestra. Comparable, que es de lo que se trata. */
    private data class Texto(val puntos: String, val sets: String)

    /**
     * Qué hacer con la notificación después de leer el disco. Antes esto eran dos
     * `Texto` centinela con contenidos imposibles —uno de ellos, un carácter NUL dentro
     * del literal, que dejaba el archivo fuente sin ser texto para `grep` y compañía—.
     * Un tipo cerrado dice lo mismo y el `when` queda exhaustivo.
     */
    private sealed interface Aviso {
        /** Todavía no hay nada escrito en disco: se deja la notificación de arranque. */
        data object Esperar : Aviso

        /** Partido terminado, o tan viejo que ya no es un partido: apagar el servicio. */
        data object Apagar : Aviso

        data class Mostrar(val texto: Texto) : Aviso
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = MatchRepository(this)
        crearCanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            detener()
            return START_NOT_STICKY
        }

        // Arrancar con algo ya dibujado: `startForeground` tiene que llamarse enseguida.
        // Si ya se estaba publicando un marcador (el usuario reabrió la app), se repite ése
        // en vez del texto de arranque: `distinctUntilChanged` no va a volver a emitirlo.
        startForeground(ID_NOTIFICACION, construirNotificacion(ultimo ?: INICIAL))

        // `onStartCommand` puede repetirse (el usuario reabre la app, `START_STICKY`
        // resucita el servicio): un solo observador, no uno por llamada.
        if (!observando) {
            observando = true
            enPantalla
                // Con la app delante no hay a quién mostrarle la notificación: se corta el
                // flujo entero, así ni siquiera se parsea el JSON en cada punto. Al volver
                // al watch face el flujo se vuelve a suscribir y publica el marcador de una vez.
                .flatMapLatest { visible -> if (visible) emptyFlow() else repo.partido }
                .map { guardado ->
                    val match = guardado?.match
                    when {
                        match == null -> Aviso.Esperar
                        // Terminado, o un partido olvidado de anteayer que revivió con el
                        // servicio: la Ongoing Activity deja de tener sentido.
                        match.matchOver -> Aviso.Apagar
                        vencido(guardado.startedAt) -> Aviso.Apagar
                        else -> Aviso.Mostrar(texto(match))
                    }
                }
                .distinctUntilChanged()
                .onEach { aviso ->
                    when (aviso) {
                        // Ojo: parar en `Esperar` mataba el servicio antes de que el
                        // ViewModel alcanzara a guardar el primer punto.
                        Aviso.Esperar -> Unit
                        Aviso.Apagar -> detener()
                        is Aviso.Mostrar -> notificar(aviso.texto)
                    }
                }
                .launchIn(scope)
        }

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

    private fun texto(match: MatchState) = Texto(
        puntos = "${match.pointLabel(Side.HOME)} - ${match.pointLabel(Side.AWAY)}",
        sets = (0 until match.rules.maxSets).joinToString(" ") { i ->
            "${match.home.sets.getOrNull(i) ?: 0}-${match.away.sets.getOrNull(i) ?: 0}"
        },
    )

    private fun notificar(texto: Texto) {
        if (texto == ultimo) return
        ultimo = texto
        notificador?.notify(ID_NOTIFICACION, construirNotificacion(texto))
    }

    private fun construirNotificacion(texto: Texto): Notification {
        val builder = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_partido)
            .setContentTitle(texto.puntos)
            .setContentText(texto.sets.ifEmpty { getString(R.string.app_name) })
            .setContentIntent(intentAbrir)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Nada de puentear al teléfono: es el ahorro grande de esta clase.
            .setLocalOnly(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(R.drawable.ic_partido, "Cerrar", intentCerrar)

        // Esto es lo que convierte una notificación normal en un ítem del watch face.
        OngoingActivity.Builder(this, ID_NOTIFICACION, builder)
            .setStaticIcon(R.drawable.ic_partido)
            .setTouchIntent(intentAbrir)
            .setStatus(Status.Builder().addTemplate(texto.puntos).build())
            .build()
            .apply(this)

        return builder.build()
    }

    private fun crearCanal() {
        // El canal viejo era `IMPORTANCE_DEFAULT` y la importancia de un canal ya creado no
        // se puede bajar por código: hay que borrarlo y crear otro con id distinto.
        notificador?.deleteNotificationChannel(CANAL_VIEJO)
        val canal = NotificationChannel(
            CANAL,
            "Partido en curso",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        notificador?.createNotificationChannel(canal)
    }

    private fun vencido(startedAt: Long) =
        System.currentTimeMillis() - startedAt > HORAS_MAXIMAS * 3_600_000L

    companion object {
        private const val CANAL_VIEJO = "partido"
        private const val CANAL = "partido_silencioso"
        private const val ID_NOTIFICACION = 1
        private const val ACCION_DETENER = "cl.matchpoint.marcador.wear.DETENER"

        /** Un partido que lleva más de esto abierto es un olvido, no un partido. */
        private const val HORAS_MAXIMAS = 8

        /** Lo que se muestra hasta que se lee el primer marcador del disco. */
        private val INICIAL = Texto("Partido en curso", "")

        /**
         * Verdadero mientras la actividad esté en pantalla (ambient incluido). Es un
         * `StateFlow` estático y no un binding porque servicio y actividad viven en el
         * mismo proceso y lo único que se comparte es un booleano.
         */
        private val enPantalla = MutableStateFlow(false)

        /** La actividad avisa al entrar y salir de pantalla. Ver el punto 5 de la clase. */
        fun uiVisible(visible: Boolean) {
            enPantalla.value = visible
        }

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
