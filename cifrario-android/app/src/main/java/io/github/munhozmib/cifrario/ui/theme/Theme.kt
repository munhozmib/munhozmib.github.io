package io.github.munhozmib.cifrario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.munhozmib.cifrario.data.ThemeMode

// Identidade visual: azul-noite (palco) + âmbar (palheta).
val Midnight = Color(0xFF14213D)
val MidnightLight = Color(0xFF233B66)
val Amber = Color(0xFFFCA311)
val AmberDeep = Color(0xFFB86E00)

private val Light = lightColorScheme(
    primary = MidnightLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Midnight,
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF3D2300),
    tertiary = AmberDeep,
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFBF8F3),
    surfaceContainer = Color(0xFFF1ECE4),
)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1B00),
    primaryContainer = Color(0xFF5A3A00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFAFC6FF),
    onSecondary = Midnight,
    secondaryContainer = MidnightLight,
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Amber,
    background = Color(0xFF0E1628),
    surface = Color(0xFF0E1628),
    surfaceContainer = Color(0xFF17223A),
)

data class SheetColors(val chord: Color, val section: Color, val tab: Color)

val LocalSheetColors = staticCompositionLocalOf { SheetColors(AmberDeep, MidnightLight, Color.Gray) }

@Composable
fun CifrarioTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val sheet = if (dark) SheetColors(Amber, Color(0xFFAFC6FF), Color(0xFF9AA4B8))
    else SheetColors(Color(0xFFC25E00), MidnightLight, Color(0xFF5F6673))
    CompositionLocalProvider(LocalSheetColors provides sheet) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
