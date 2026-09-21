package io.shikimove.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF0C0E14)
val Panel = Color(0xFF171A24)
val Muted = Color(0xFF9B9DAC)
val Lavender = Color(0xFFB9ACFF)

@Composable fun ShikiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Lavender, onPrimary = Color(0xFF26203F),
            primaryContainer = Color(0xFF312B47), background = Night, surface = Night,
            surfaceVariant = Panel, onSurface = Color(0xFFF0EFF6), onSurfaceVariant = Muted,
            outline = Color(0xFF383B49), secondary = Color(0xFF91D2BD)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        ), content = content,
    )
}
