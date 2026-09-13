package cl.matchpoint.marcador.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.matchpoint.marcador.wear.domain.MatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * El partido tal como se guarda en disco: el estado más los dos instantes que necesita
 * el cronómetro. Sin esto, apagar la pantalla o que el sistema mate la app en medio de
 * un partido de dos horas se lleva el marcador.
 */
@Serializable
data class PartidoGuardado(
    val match: MatchState,
    val startedAt: Long,
    /** Instante en que terminó el partido; `null` mientras siga en juego. */
    val stoppedAt: Long? = null,
)

/**
 * Todo lo que la app necesita del disco para arrancar. Se lee de una vez porque abrir el
 * DataStore es lo más caro del arranque, y el arranque es un pico de CPU en un reloj.
 */
data class EstadoInicial(
    val partido: PartidoGuardado?,
    val nombres: Pair<String, String>,
    /**
     * Pantalla siempre encendida (ambient) mientras dure el partido. Es, de lejos, lo que
     * más batería gasta de la app: mantiene el OLED encendido las dos horas del partido en
     * vez de dejar que el reloj se apague. Encendido por omisión —es la razón de ser de un
     * marcador de muñeca— pero el usuario puede apagarlo desde la pantalla de preparación.
     */
    val siempreEncendido: Boolean,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "partido")

/**
 * Persistencia del partido en curso. Se usa DataStore de preferencias con **una** clave
 * que lleva el JSON completo: el estado es un objeto anidado (dos jugadores, listas de
 * games, reglas) y desarmarlo en claves sueltas sólo abriría la puerta a guardarlo a medias.
 */
class MatchRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    /**
     * `null` cuando no hay partido guardado, o cuando lo guardado ya no se puede leer.
     *
     * Batería: `store.data` emite ante *cualquier* cambio del archivo (también al guardar
     * los nombres), y deserializar el JSON no es gratis. Por eso el `map` va en
     * [Dispatchers.Default] — nunca en el hilo de UI — y con [distinctUntilChanged] para
     * no despertar al consumidor (el servicio, que reconstruye una notificación) cuando
     * lo que cambió no fue el partido.
     */
    val partido: Flow<PartidoGuardado?> = store.data
        .map { prefs -> prefs[CLAVE] }
        .distinctUntilChanged()
        .map { texto ->
            // Un JSON viejo (de una versión anterior del modelo) no debe dejar la app
            // sin arrancar: se descarta y se empieza un partido nuevo.
            texto?.let { runCatching { json.decodeFromString<PartidoGuardado>(it) }.getOrNull() }
        }
        .flowOn(Dispatchers.Default)

    suspend fun guardar(partido: PartidoGuardado) {
        val texto = json.encodeToString(PartidoGuardado.serializer(), partido)
        // Cada `edit` reescribe el archivo entero y hace fsync. Si el JSON es idéntico al
        // que ya está en disco no se toca la flash: el ViewModel guarda por tiempo, no
        // sólo por cambio, y un partido en pausa no tiene por qué escribir nada.
        store.edit { if (it[CLAVE] != texto) it[CLAVE] = texto }
    }

    /**
     * Últimos nombres usados y preferencia de pantalla siempre encendida, en **una sola
     * lectura** del archivo. Antes el ViewModel hacía `first()` sobre varios flujos por
     * separado, y eso abría y parseaba el DataStore una vez por cada uno en el arranque.
     */
    suspend fun cargarInicial(): EstadoInicial = withContext(Dispatchers.Default) {
        val prefs = store.data.first()
        val guardado = prefs[CLAVE]?.let {
            runCatching { json.decodeFromString<PartidoGuardado>(it) }.getOrNull()
        }
        EstadoInicial(
            partido = guardado,
            nombres = (prefs[LOCAL] ?: "Yo") to (prefs[RIVAL] ?: "Rival"),
            siempreEncendido = prefs[SIEMPRE_ENCENDIDO] ?: true,
        )
    }

    suspend fun guardarNombres(local: String, rival: String) {
        store.edit {
            it[LOCAL] = local
            it[RIVAL] = rival
        }
    }

    /** Ver [EstadoInicial.siempreEncendido]. */
    suspend fun guardarSiempreEncendido(activo: Boolean) {
        store.edit { it[SIEMPRE_ENCENDIDO] = activo }
    }

    private companion object {
        val CLAVE = stringPreferencesKey("partido_json")
        val LOCAL = stringPreferencesKey("nombre_local")
        val RIVAL = stringPreferencesKey("nombre_rival")
        val SIEMPRE_ENCENDIDO = booleanPreferencesKey("siempre_encendido")
        val json = Json { ignoreUnknownKeys = true }
    }
}
