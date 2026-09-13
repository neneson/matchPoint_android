package cl.matchpoint.marcador.wear.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de Matchpoint, traducida 1:1 desde los tokens `oklch(...)` de
 * `src/styles.css` (la app web) a sRGB. Los nombres son los mismos que allá para
 * poder comparar pantalla contra pantalla.
 */
object MatchpointColors {
    /**
     * Negro puro, no el `#191A1F` de la web. En un OLED cada píxel apagado es corriente
     * que no se pide, y el fondo es la superficie más grande de la pantalla: es el ahorro
     * más barato que hay, y a simple vista no se distingue del gris de la web.
     */
    val background = Color(0xFF000000)
    val foreground = Color(0xFFFCFCFC)

    /**
     * Relleno de los dos botones de punto. Era `#2C2E33` cubriendo media pantalla; ahora
     * es casi negro y el botón se delimita con [cardBorder]. **Si algún día se prefiere el
     * aspecto de la web por encima de la batería, estos dos valores son lo único que hay
     * que revertir.**
     */
    val card = Color(0xFF0B0C0F)
    val cardBorder = Color(0xFF2C2E33)
    val cardForeground = Color(0xFFFCFCFC)
    val primary = Color(0xFF1779E1)
    val secondary = Color(0xFF383A40)
    val muted = Color(0xFF313338)
    val mutedForeground = Color(0xFF9FA4B2)
    val accent = Color(0xFFD29A00)
    val accentForeground = Color(0xFF080B14)
    val destructive = Color(0xFFEE343B)

    /** Set en juego: azul eléctrico. */
    val setActive = Color(0xFF006DE2)

    /** Set terminado: azul marino. */
    val setDone = Color(0xFF13335A)

    /** Pelota de saque. */
    val ball = Color(0xFF97B300)

    /** Fondo de la fila 0 (cronómetro + hora). */
    val navy = Color(0xFF112555)

    /** Ambient mode (Fase 4): negro OLED puro, no el fondo gris azulado. */
    val ambientBackground = Color(0xFF000000)
}
