package com.hotelapp.staff.core.design

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class ThemePreference {
    SYSTEM,
    DARK,
    LIGHT,
}

val LocalHotelColors = staticCompositionLocalOf { DarkHotelColors }

private val HotelTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
    ),
)

private val HotelShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun HotelTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    val tokens = if (useDark) DarkHotelColors else LightHotelColors

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = tokens.primaryMain,
            onPrimary = tokens.primaryContrastText,
            primaryContainer = tokens.primarySubtle,
            onPrimaryContainer = tokens.primaryOnSurface,
            secondary = tokens.secondaryMain,
            onSecondary = tokens.secondaryContrastText,
            secondaryContainer = tokens.primarySubtle,
            onSecondaryContainer = tokens.primaryOnSurface,
            background = tokens.surfaceApp,
            onBackground = tokens.textPrimary,
            surface = tokens.surfaceBase,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceRaised,
            onSurfaceVariant = tokens.textSecondary,
            surfaceContainerLowest = tokens.surfaceSunken,
            surfaceContainerLow = tokens.surfaceBase,
            surfaceContainer = tokens.surfaceRaised,
            surfaceContainerHigh = tokens.surfaceOverlay,
            surfaceContainerHighest = tokens.surfaceHover,
            outline = tokens.borderBase,
            outlineVariant = tokens.borderSubtle,
            scrim = tokens.scrim,
            error = tokens.danger.fg,
            onError = tokens.primaryContrastText,
        )
    } else {
        lightColorScheme(
            primary = tokens.primaryMain,
            onPrimary = tokens.primaryContrastText,
            primaryContainer = tokens.primarySubtle,
            onPrimaryContainer = tokens.primaryOnSurface,
            secondary = tokens.secondaryMain,
            onSecondary = tokens.secondaryContrastText,
            secondaryContainer = tokens.primarySubtle,
            onSecondaryContainer = tokens.primaryOnSurface,
            background = tokens.surfaceApp,
            onBackground = tokens.textPrimary,
            surface = tokens.surfaceBase,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceRaised,
            onSurfaceVariant = tokens.textSecondary,
            surfaceContainerLowest = tokens.surfaceSunken,
            surfaceContainerLow = tokens.surfaceBase,
            surfaceContainer = tokens.surfaceRaised,
            surfaceContainerHigh = tokens.surfaceOverlay,
            surfaceContainerHighest = tokens.surfaceHover,
            outline = tokens.borderBase,
            outlineVariant = tokens.borderSubtle,
            scrim = tokens.scrim,
            error = tokens.danger.fg,
            onError = tokens.secondaryContrastText,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, useDark) {
            val activity = view.context.findActivity()
            if (activity != null) {
                val barStyle = if (useDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                activity.enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
            }
            onDispose { }
        }
    }

    CompositionLocalProvider(LocalHotelColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HotelTypography,
            shapes = HotelShapes,
            content = content,
        )
    }
}

object HotelTheme {
    val colors: HotelExtendedColors
        @Composable
        get() = LocalHotelColors.current
}
