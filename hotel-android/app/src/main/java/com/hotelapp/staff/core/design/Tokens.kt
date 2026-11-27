package com.hotelapp.staff.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusTone(
    val fg: Color,
    val bg: Color,
    val border: Color,
)

@Immutable
data class HotelExtendedColors(
    val isDark: Boolean,
    // Surfaces
    val surfaceApp: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceSunken: Color,
    val surfaceHover: Color,
    val surfaceActive: Color,
    val surfaceSelected: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    // Borders
    val borderSubtle: Color,
    val borderBase: Color,
    val borderStrong: Color,
    // Accents
    val primaryMain: Color,
    val primaryContrastText: Color,
    val primaryOnSurface: Color,
    val primarySubtle: Color,
    val primarySubtleBorder: Color,
    val secondaryMain: Color,
    val secondaryContrastText: Color,
    val scrim: Color,
    // Status tones
    val success: StatusTone,
    val warning: StatusTone,
    val danger: StatusTone,
    val info: StatusTone,
    val neutral: StatusTone,
    val orange: StatusTone,
    val violet: StatusTone,
)

val DarkHotelColors = HotelExtendedColors(
    isDark = true,
    surfaceApp = Color(0xFF0B0E13),
    surfaceBase = Color(0xFF12161D),
    surfaceRaised = Color(0xFF171C24),
    surfaceOverlay = Color(0xFF1C222C),
    surfaceSunken = Color(0xFF0D1117),
    surfaceHover = Color(0xFF222936),
    surfaceActive = Color(0xFF29313F),
    surfaceSelected = Color(0x1FC9A96A),
    textPrimary = Color(0xFFE9EAF0),
    textSecondary = Color(0xFFA7AEBC),
    textMuted = Color(0xFF818A99),
    textDisabled = Color(0xFF59616F),
    borderSubtle = Color(0xFF202733),
    borderBase = Color(0xFF2A3242),
    borderStrong = Color(0xFF3C4659),
    primaryMain = Color(0xFFC9A96A),
    primaryContrastText = Color(0xFF1A1410),
    primaryOnSurface = Color(0xFFD8BD85),
    primarySubtle = Color(0x24C9A96A),
    primarySubtleBorder = Color(0x61C9A96A),
    secondaryMain = Color(0xFF7FA8DC),
    secondaryContrastText = Color(0xFF10151D),
    scrim = Color(0x9E04060A),
    success = StatusTone(
        fg = Color(0xFF5FBF8F),
        bg = Color(0x215FBF8F),
        border = Color(0x525FBF8F),
    ),
    warning = StatusTone(
        fg = Color(0xFFE3A63F),
        bg = Color(0x21E3A63F),
        border = Color(0x52E3A63F),
    ),
    danger = StatusTone(
        fg = Color(0xFFE37670),
        bg = Color(0x21E16D66),
        border = Color(0x52E16D66),
    ),
    info = StatusTone(
        fg = Color(0xFF7FA8DC),
        bg = Color(0x217FA8DC),
        border = Color(0x527FA8DC),
    ),
    neutral = StatusTone(
        fg = Color(0xFF9AA2B0),
        bg = Color(0x1F9AA2B0),
        border = Color(0x429AA2B0),
    ),
    orange = StatusTone(
        fg = Color(0xFFE08A48),
        bg = Color(0x21E08A48),
        border = Color(0x52E08A48),
    ),
    violet = StatusTone(
        fg = Color(0xFFA091C1),
        bg = Color(0x219E8FC0),
        border = Color(0x529E8FC0),
    ),
)

val LightHotelColors = HotelExtendedColors(
    isDark = false,
    surfaceApp = Color(0xFFF3F0E9),
    surfaceBase = Color(0xFFFCFBF7),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceOverlay = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFECE8DE),
    surfaceHover = Color(0xFFEDE9DF),
    surfaceActive = Color(0xFFE3DDCE),
    surfaceSelected = Color(0x1A8A6A33),
    textPrimary = Color(0xFF1C1E24),
    textSecondary = Color(0xFF535A68),
    textMuted = Color(0xFF555C69),
    textDisabled = Color(0xFFA6ACB8),
    borderSubtle = Color(0xFFE6E1D4),
    borderBase = Color(0xFFD6CFBD),
    borderStrong = Color(0xFFB4AB93),
    primaryMain = Color(0xFF8A6A33),
    primaryContrastText = Color(0xFFFFFDF7),
    primaryOnSurface = Color(0xFF77592A),
    primarySubtle = Color(0x1F8A6A33),
    primarySubtleBorder = Color(0x598A6A33),
    secondaryMain = Color(0xFF4A6FA0),
    secondaryContrastText = Color(0xFFFFFFFF),
    scrim = Color(0x731C1912),
    success = StatusTone(
        fg = Color(0xFF1C7249),
        bg = Color(0x1A1E7A4E),
        border = Color(0x4D1E7A4E),
    ),
    warning = StatusTone(
        fg = Color(0xFF885C0E),
        bg = Color(0x1A96650F),
        border = Color(0x4D96650F),
    ),
    danger = StatusTone(
        fg = Color(0xFFB23A30),
        bg = Color(0x17B23A30),
        border = Color(0x4DB23A30),
    ),
    info = StatusTone(
        fg = Color(0xFF2F639E),
        bg = Color(0x1A2F639E),
        border = Color(0x4D2F639E),
    ),
    neutral = StatusTone(
        fg = Color(0xFF5D6470),
        bg = Color(0x175D6470),
        border = Color(0x3D5D6470),
    ),
    orange = StatusTone(
        fg = Color(0xFF98531A),
        bg = Color(0x1AA85B1D),
        border = Color(0x4DA85B1D),
    ),
    violet = StatusTone(
        fg = Color(0xFF6C5B9C),
        bg = Color(0x1A7A6AA8),
        border = Color(0x4D7A6AA8),
    ),
)
