package com.wakeme.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ColorSchemePreset(val label: String) {
    SIGNAL("1. Signal Ink"),
    INDIGO("2. Bengaluru Indigo"),
    BOTANICAL("3. Botanical Transit"),
    NORDIC("4. Nordic Slate"),
    ESPRESSO("5. Espresso Copper")
}

@Immutable
data class WakeMePalette(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val orange: Color,
    val blue: Color,
    val purple: Color,
    val green: Color,
    val red: Color,
    val cueBg: Color,
    val cueInk: Color,
    val ctaBg: Color,
    val ctaInk: Color,
    val badgeInk: Color
)

fun paletteFor(preset: ColorSchemePreset, dark: Boolean): WakeMePalette = when (preset) {
    ColorSchemePreset.SIGNAL -> if (!dark) WakeMePalette(
        bg = Color(0xFFF3F4F6), surface = Color(0xFFFFFFFF), ink = Color(0xFF0F172A),
        muted = Color(0xFF475569), hairline = Color(0x140F172A), orange = Color(0xFFF57C00),
        blue = Color(0xFF1E88E5), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), cueBg = Color(0xEB0F172A), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF0F172A), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF171A21), surface = Color(0xFF0F1117), ink = Color(0xFFF8FAFC),
        muted = Color(0xFF94A3B8), hairline = Color(0x1AF8FAFC), orange = Color(0xFFFF9800),
        blue = Color(0xFF60A5FA), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), cueBg = Color(0xF21E2430), cueInk = Color(0xFFF8FAFC),
        ctaBg = Color(0xFFF8FAFC), ctaInk = Color(0xFF0F172A), badgeInk = Color(0xFF0F172A)
    )

    ColorSchemePreset.INDIGO -> if (!dark) WakeMePalette(
        bg = Color(0xFFEEF1F7), surface = Color(0xFFFFFFFF), ink = Color(0xFF15192B),
        muted = Color(0xFF535B78), hairline = Color(0x1415192B), orange = Color(0xFFF97316),
        blue = Color(0xFF4338CA), purple = Color(0xFF8E24AA), green = Color(0xFF15803D),
        red = Color(0xFFDC2626), cueBg = Color(0xEE1E243B), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF312E81), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1E243B), surface = Color(0xFF131726), ink = Color(0xFFEEF2FF),
        muted = Color(0xFF94A3B8), hairline = Color(0x1AE0E7FF), orange = Color(0xFFFB923C),
        blue = Color(0xFF818CF8), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), cueBg = Color(0xF51E243B), cueInk = Color(0xFFEEF2FF),
        ctaBg = Color(0xFF60A5FA), ctaInk = Color(0xFF0F172A), badgeInk = Color(0xFF0F172A)
    )

    ColorSchemePreset.BOTANICAL -> if (!dark) WakeMePalette(
        bg = Color(0xFFECF2EE), surface = Color(0xFFF8F7F3), ink = Color(0xFF14291D),
        muted = Color(0xFF4E6658), hairline = Color(0x1714291D), orange = Color(0xFFC2410C),
        blue = Color(0xFF0F766E), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), cueBg = Color(0xF014291D), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF14532D), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1C2922), surface = Color(0xFF121A15), ink = Color(0xFFECFDF5),
        muted = Color(0xFF8FA899), hairline = Color(0x17ECFDF5), orange = Color(0xFFFB923C),
        blue = Color(0xFF2DD4BF), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), cueBg = Color(0xF21C2922), cueInk = Color(0xFFECFDF5),
        ctaBg = Color(0xFF4ADE80), ctaInk = Color(0xFF052E16), badgeInk = Color(0xFF052E16)
    )

    ColorSchemePreset.NORDIC -> if (!dark) WakeMePalette(
        bg = Color(0xFFE2E8F0), surface = Color(0xFFF8FAFC), ink = Color(0xFF0F172A),
        muted = Color(0xFF475569), hairline = Color(0x140F172A), orange = Color(0xFFEA580C),
        blue = Color(0xFF0284C7), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), cueBg = Color(0xEB0F172A), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF0284C7), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1E293B), surface = Color(0xFF0F172A), ink = Color(0xFFF8FAFC),
        muted = Color(0xFF94A3B8), hairline = Color(0x17F8FAFC), orange = Color(0xFFFB923C),
        blue = Color(0xFF38BDF8), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), cueBg = Color(0xF21E293B), cueInk = Color(0xFFF8FAFC),
        ctaBg = Color(0xFF38BDF8), ctaInk = Color(0xFF082F49), badgeInk = Color(0xFF082F49)
    )

    ColorSchemePreset.ESPRESSO -> if (!dark) WakeMePalette(
        bg = Color(0xFFEFECE6), surface = Color(0xFFFAF8F5), ink = Color(0xFF1C1917),
        muted = Color(0xFF6B635B), hairline = Color(0x171C1917), orange = Color(0xFFC2410C),
        blue = Color(0xFF0369A1), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), cueBg = Color(0xF01C1917), cueInk = Color(0xFFFAF8F5),
        ctaBg = Color(0xFF292524), ctaInk = Color(0xFFFAF8F5), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF26221F), surface = Color(0xFF171412), ink = Color(0xFFF5F5F4),
        muted = Color(0xFFA8A29E), hairline = Color(0x17F5F5F4), orange = Color(0xFFFB923C),
        blue = Color(0xFF60A5FA), purple = Color(0xFFD8B4FE), green = Color(0xFF86EFAC),
        red = Color(0xFFF87171), cueBg = Color(0xF526221F), cueInk = Color(0xFFF5F5F4),
        ctaBg = Color(0xFFFB923C), ctaInk = Color(0xFF1C1917), badgeInk = Color(0xFF1C1917)
    )
}

object WakeMeMotion {
    const val DURATION_MS = 480
    const val PRESS_SCALE = 0.93f

    fun elasticFloatSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow)

    fun elasticOffsetSpec(): FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessMediumLow)
}
