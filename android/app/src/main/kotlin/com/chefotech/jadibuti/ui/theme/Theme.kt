package com.chefotech.jadibuti.ui.theme

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

/*
 * Jadi-Buti palette: a restrained clinical green, neutral surfaces and a small set of
 * status colours. The header colour is fixed in both light and dark mode so the brand bar
 * always reads the same.
 */
val Green = Color(0xFF1E6B47)
val GreenDark = Color(0xFF14513A)
val HeaderGreen = Green
val Leaf = Color(0xFF3F8F63)
val Cream = Color(0xFFF4F7F5)
val Amber = Color(0xFFB7791F)
val AmberSoft = Color(0xFFFBF1DC)
val Red = Color(0xFFC0392B)
val RedSoft = Color(0xFFFBE9E7)
val GreenSoft = Color(0xFFDFEFE6)
val Blue = Color(0xFF1F5FA8)
val BlueSoft = Color(0xFFE4EDF8)
val Grey = Color(0xFF64716A)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenDark,
    secondary = Leaf,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F1EA),
    tertiary = Blue,
    background = Cream,
    onBackground = Color(0xFF16231C),
    surface = Color.White,
    onSurface = Color(0xFF16231C),
    surfaceVariant = Color(0xFFEAF0EC),
    onSurfaceVariant = Color(0xFF4F5E56),
    error = Red,
    errorContainer = RedSoft,
    outline = Color(0xFFCBD5CF),
    outlineVariant = Color(0xFFE1E8E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCFA3),
    onPrimary = Color(0xFF0B3B25),
    primaryContainer = Color(0xFF1B4D36),
    onPrimaryContainer = Color(0xFFD5F0DF),
    secondary = Color(0xFF8CC7A4),
    tertiary = Color(0xFF8FB8EA),
    background = Color(0xFF0F1613),
    onBackground = Color(0xFFE6EDE8),
    surface = Color(0xFF161E1A),
    onSurface = Color(0xFFE6EDE8),
    surfaceVariant = Color(0xFF1F2A24),
    onSurfaceVariant = Color(0xFFB4C1B9),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF35453C),
    outlineVariant = Color(0xFF2A3730),
)

/** Large, readable typography for elderly users. */
val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun JadiButiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
}
