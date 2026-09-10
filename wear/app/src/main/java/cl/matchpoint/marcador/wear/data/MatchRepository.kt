package cl.matchpoint.marcador.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.matchpoint.marcador.wear.domain.MatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "partido")

/**
 * Persistencia del partido en curso. Se usa DataStore de preferencias con **una** clave
 * que lleva el JSON completo: el estado es un objeto anidado (dos jugadores, listas de
 * games, reglas) y desarmarlo en claves sueltas sólo abriría la puerta a guardarlo a medias.
 */
class MatchRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    /** `null` cuando no hay partido guardado, o cuando lo guardado ya no se puede leer. */
    val partido: Flow<PartidoGuardado?> = store.data.map { prefs ->
        prefs[CLAVE]?.let { texto ->
            // Un JSON viejo (de una versión anterior del modelo) no debe dejar la app
            // sin arrancar: se descarta y se empieza un partido nuevo.
            runCatching { json.decodeFromString<PartidoGuardado>(texto) }.getOrNull()
        }
    }

    suspend fun guardar(partido: PartidoGuardado) {
        store.edit { it[CLAVE] = json.encodeToString(PartidoGuardado.serializer(), partido) }
    }

    suspend fun borrar() {
        store.edit { it.remove(CLAVE) }
    }

    /**
     * Últimos nombres usados. Dictar un nombre en un reloj cuesta, así que el siguiente
     * partido arranca con los del anterior ya puestos.
     */
    val nombres: Flow<Pair<String, String>> = store.data.map { prefs ->
        (prefs[LOCAL] ?: "Yo") to (prefs[RIVAL] ?: "Rival")
    }

    suspend fun guardarNombres(local: String, rival: String) {
        store.edit {
            it[LOCAL] = local
            it[RIVAL] = rival
        }
    }

    private companion object {
        val CLAVE = stringPreferencesKey("partido_json")
        val LOCAL = stringPreferencesKey("nombre_local")
        val RIVAL = stringPreferencesKey("nombre_rival")
        val json = Json { ignoreUnknownKeys = true }
    }
}
