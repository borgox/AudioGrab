package com.example.audiograb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color(0xFFFDF6EE),
    secondary = Olive,
    onSecondary = Color(0xFFFDF6EE),
    tertiary = DeepClay,
    onTertiary = Color(0xFFFDF6EE),
    background = Cream,
    onBackground = Cocoa,
    surface = Color(0xFFFFFBF6),
    onSurface = Cocoa,
    surfaceVariant = Mist,
    onSurfaceVariant = Cocoa,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Terracotta,
    onPrimary = Color(0xFF2D1910),
    secondary = Sand,
    onSecondary = Color(0xFF2D1910),
    tertiary = Sand,
    onTertiary = Color(0xFF2D1910),
    background = Color(0xFF1B1410),
    onBackground = Color(0xFFF7EBDD),
    surface = Color(0xFF241B16),
    onSurface = Color(0xFFF7EBDD),
    surfaceVariant = Color(0xFF2C221C),
    onSurfaceVariant = Color(0xFFF7EBDD),
    error = Color(0xFFF2B8B5)
)

private val AppTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )
)

@Composable
fun AudioGrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
