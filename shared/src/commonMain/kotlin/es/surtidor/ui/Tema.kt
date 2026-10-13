package es.surtidor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta sobria, la misma idea que el resto del portfolio: un único acento y grises.
private val Claro =
    lightColorScheme(
        primary = Color(0xFF1D4ED8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3EAFC),
        onPrimaryContainer = Color(0xFF0F2A73),
        secondary = Color(0xFF475569),
        secondaryContainer = Color(0xFFE6EBF1),
        onSecondaryContainer = Color(0xFF1C2430),
        tertiary = Color(0xFF15703A),
        tertiaryContainer = Color(0xFFE2F4E9),
        onTertiaryContainer = Color(0xFF0E4A27),
        background = Color(0xFFF6F7F9),
        onBackground = Color(0xFF1C2430),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C2430),
        surfaceVariant = Color(0xFFEEF1F5),
        onSurfaceVariant = Color(0xFF556070),
        surfaceContainer = Color(0xFFF1F3F6),
        outline = Color(0xFFB5BFCC),
        outlineVariant = Color(0xFFD7DDE5),
        error = Color(0xFFA61B1B),
    )

private val Oscuro =
    darkColorScheme(
        primary = Color(0xFF7EA2FF),
        onPrimary = Color(0xFF0D1420),
        primaryContainer = Color(0xFF1F2C4A),
        onPrimaryContainer = Color(0xFFD6E0FF),
        secondary = Color(0xFF9BA7B6),
        secondaryContainer = Color(0xFF263040),
        onSecondaryContainer = Color(0xFFE5E9EF),
        tertiary = Color(0xFF6FD29A),
        tertiaryContainer = Color(0xFF163423),
        onTertiaryContainer = Color(0xFFBDEBD0),
        background = Color(0xFF11161C),
        onBackground = Color(0xFFE5E9EF),
        surface = Color(0xFF182029),
        onSurface = Color(0xFFE5E9EF),
        surfaceVariant = Color(0xFF202A35),
        onSurfaceVariant = Color(0xFF9BA7B6),
        surfaceContainer = Color(0xFF1C242E),
        outline = Color(0xFF435163),
        outlineVariant = Color(0xFF2D3946),
        error = Color(0xFFFF9B9B),
    )

/** Cifras tabulares: los precios de una lista quedan alineados. */
val estiloPrecio = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)

@Composable
fun TemaSurtidor(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (oscuro) Oscuro else Claro,
        typography =
            base.copy(
                headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            ),
        content = contenido,
    )
}
