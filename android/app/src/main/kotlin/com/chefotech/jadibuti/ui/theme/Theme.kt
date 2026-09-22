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

val Green = Color(0xFF2E7D4F)
val GreenDark = Color(0xFF1F5E3A)
val Leaf = Color(0xFF7CB342)
val Cream = Color(0xFFF6FAF6)
val Amber = Color(0xFFF9A825)
val AmberSoft = Color(0xFFFFF3CD)
val Red = Color(0xFFD32F2F)
val RedSoft = Color(0xFFFDE7E7)
val GreenSoft = Color(0xFFE3F3E8)
val Blue = Color(0xFF1E6FB8)
val BlueSoft = Color(0xFFE3EEF9)
val Grey = Color(0xFF6B7280)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenDark,
    secondary = Leaf,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5D9),
    tertiary = Blue,
    background = Cream,
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF3EE),
    error = Red,
    errorContainer = RedSoft,
    outline = Color(0xFFB9C7BD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD3A6),
    onPrimary = Color(0xFF0E3B22),
    primaryContainer = Color(0xFF1F5E3A),
    onPrimaryContainer = Color(0xFFD9F2E1),
    secondary = Leaf,
    background = Color(0xFF111814),
    surface = Color(0xFF18211B),
    surfaceVariant = Color(0xFF243029),
    error = Color(0xFFFF8A80),
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
