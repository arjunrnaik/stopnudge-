package com.wakeme.uipreview

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

enum class PreviewScreen { LANDING, SETUP, ACTIVE }
enum class TransitMode(val label: String) { METRO("Namma Metro"), BUS("City Bus"), CAR("Direct Road") }

enum class ColorSchemePreset(val label: String) {
    SIGNAL("1. Signal Ink"),
    INDIGO("2. Bengaluru Indigo"),
    BOTANICAL("3. Botanical Transit"),
    NORDIC("4. Nordic Slate"),
    ESPRESSO("5. Espresso Copper")
}

enum class MotionPreset(val label: String) {
    EMIL("1. Emil Spring"),
    SNAPPY("2. Snappy Utility"),
    ELASTIC("3. Fluid Elastic"),
    EDITORIAL("4. Calm Editorial"),
    REDUCED("5. Zero Motion")
}

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
    val mapBg: Color,
    val mapRoadMajor: Color,
    val mapRoadMinor: Color,
    val cueBg: Color,
    val cueInk: Color,
    val ctaBg: Color,
    val ctaInk: Color,
    val badgeInk: Color
)

fun paletteFor(scheme: ColorSchemePreset, dark: Boolean): WakeMePalette = when (scheme) {
    ColorSchemePreset.SIGNAL -> if (!dark) WakeMePalette(
        bg = Color(0xFFF5F6F8), surface = Color(0xFFFFFFFF), ink = Color(0xFF0F172A),
        muted = Color(0xFF5B667A), hairline = Color(0x140F172A), orange = Color(0xFFE65100),
        blue = Color(0xFF1565C0), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), mapBg = Color(0xFFEDEFF2), mapRoadMajor = Color(0xFFDCE2E8),
        mapRoadMinor = Color(0xFFE2E6EB), cueBg = Color(0xEE0F172A), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF0F172A), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF202632), surface = Color(0xFF151921), ink = Color(0xFFF1F5F9),
        muted = Color(0xFF94A3B8), hairline = Color(0x17FFFFFF), orange = Color(0xFFFF7043),
        blue = Color(0xFF42A5F5), purple = Color(0xFFBA68C8), green = Color(0xFF66BB6A),
        red = Color(0xFFEF5350), mapBg = Color(0xFF11151D), mapRoadMajor = Color(0xFF1E2533),
        mapRoadMinor = Color(0xFF181E2A), cueBg = Color(0xF21E2430), cueInk = Color(0xFFF8FAFC),
        ctaBg = Color(0xFFF8FAFC), ctaInk = Color(0xFF0F172A), badgeInk = Color(0xFF0F172A)
    )

    ColorSchemePreset.INDIGO -> if (!dark) WakeMePalette(
        bg = Color(0xFFEEF2FF), surface = Color(0xFFFAF9F6), ink = Color(0xFF1E1B4B),
        muted = Color(0xFF4F5678), hairline = Color(0x171E1B4B), orange = Color(0xFFD97706),
        blue = Color(0xFF1D4ED8), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), mapBg = Color(0xFFEAEDF6), mapRoadMajor = Color(0xFFD6DCF0),
        mapRoadMinor = Color(0xFFE0E5F4), cueBg = Color(0xF01E1B4B), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF1E3A8A), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1E243B), surface = Color(0xFF131626), ink = Color(0xFFEEF2FF),
        muted = Color(0xFF9CA6C9), hairline = Color(0x1AEEF2FF), orange = Color(0xFFFBBF24),
        blue = Color(0xFF60A5FA), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), mapBg = Color(0xFF0D101C), mapRoadMajor = Color(0xFF1B2138),
        mapRoadMinor = Color(0xFF14192B), cueBg = Color(0xF51E243B), cueInk = Color(0xFFEEF2FF),
        ctaBg = Color(0xFF60A5FA), ctaInk = Color(0xFF0F172A), badgeInk = Color(0xFF0F172A)
    )

    ColorSchemePreset.BOTANICAL -> if (!dark) WakeMePalette(
        bg = Color(0xFFECF2EE), surface = Color(0xFFF8F7F3), ink = Color(0xFF14291D),
        muted = Color(0xFF4E6658), hairline = Color(0x1714291D), orange = Color(0xFFC2410C),
        blue = Color(0xFF0F766E), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), mapBg = Color(0xFFE6ECE7), mapRoadMajor = Color(0xFFD1DDD4),
        mapRoadMinor = Color(0xFFDCE5DE), cueBg = Color(0xF014291D), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF14532D), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1C2922), surface = Color(0xFF121A15), ink = Color(0xFFECFDF5),
        muted = Color(0xFF8FA899), hairline = Color(0x17ECFDF5), orange = Color(0xFFFB923C),
        blue = Color(0xFF2DD4BF), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), mapBg = Color(0xFF0C120E), mapRoadMajor = Color(0xFF1A261F),
        mapRoadMinor = Color(0xFF131C17), cueBg = Color(0xF21C2922), cueInk = Color(0xFFECFDF5),
        ctaBg = Color(0xFF4ADE80), ctaInk = Color(0xFF052E16), badgeInk = Color(0xFF052E16)
    )

    ColorSchemePreset.NORDIC -> if (!dark) WakeMePalette(
        bg = Color(0xFFE2E8F0), surface = Color(0xFFF8FAFC), ink = Color(0xFF0F172A),
        muted = Color(0xFF475569), hairline = Color(0x140F172A), orange = Color(0xFFEA580C),
        blue = Color(0xFF0284C7), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), mapBg = Color(0xFFE6ECF2), mapRoadMajor = Color(0xFFCFD9E3),
        mapRoadMinor = Color(0xFFDCE4EC), cueBg = Color(0xEB0F172A), cueInk = Color(0xFFFFFFFF),
        ctaBg = Color(0xFF0284C7), ctaInk = Color(0xFFFFFFFF), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF1E293B), surface = Color(0xFF0F172A), ink = Color(0xFFF8FAFC),
        muted = Color(0xFF94A3B8), hairline = Color(0x17F8FAFC), orange = Color(0xFFFB923C),
        blue = Color(0xFF38BDF8), purple = Color(0xFFC084FC), green = Color(0xFF4ADE80),
        red = Color(0xFFF87171), mapBg = Color(0xFF090E1A), mapRoadMajor = Color(0xFF1B263B),
        mapRoadMinor = Color(0xFF121B2D), cueBg = Color(0xF21E293B), cueInk = Color(0xFFF8FAFC),
        ctaBg = Color(0xFF38BDF8), ctaInk = Color(0xFF082F49), badgeInk = Color(0xFF082F49)
    )

    ColorSchemePreset.ESPRESSO -> if (!dark) WakeMePalette(
        bg = Color(0xFFEFECE6), surface = Color(0xFFFAF8F5), ink = Color(0xFF1C1917),
        muted = Color(0xFF6B635B), hairline = Color(0x171C1917), orange = Color(0xFFC2410C),
        blue = Color(0xFF0369A1), purple = Color(0xFF8E24AA), green = Color(0xFF2E7D32),
        red = Color(0xFFD32F2F), mapBg = Color(0xFFECE7DF), mapRoadMajor = Color(0xFFDBD3C7),
        mapRoadMinor = Color(0xFFE4DDD3), cueBg = Color(0xF01C1917), cueInk = Color(0xFFFAF8F5),
        ctaBg = Color(0xFF292524), ctaInk = Color(0xFFFAF8F5), badgeInk = Color(0xFFFFFFFF)
    ) else WakeMePalette(
        bg = Color(0xFF26221F), surface = Color(0xFF171412), ink = Color(0xFFF5F5F4),
        muted = Color(0xFFA8A29E), hairline = Color(0x17F5F5F4), orange = Color(0xFFFB923C),
        blue = Color(0xFF60A5FA), purple = Color(0xFFD8B4FE), green = Color(0xFF86EFAC),
        red = Color(0xFFF87171), mapBg = Color(0xFF100E0C), mapRoadMajor = Color(0xFF241F1C),
        mapRoadMinor = Color(0xFF1B1715), cueBg = Color(0xF526221F), cueInk = Color(0xFFF5F5F4),
        ctaBg = Color(0xFFFB923C), ctaInk = Color(0xFF1C1917), badgeInk = Color(0xFF1C1917)
    )
}

fun motionDurationMs(preset: MotionPreset): Int = when (preset) {
    MotionPreset.EMIL -> 340
    MotionPreset.SNAPPY -> 110
    MotionPreset.ELASTIC -> 480
    MotionPreset.EDITORIAL -> 620
    MotionPreset.REDUCED -> 0
}

fun motionSpecFor(preset: MotionPreset): FiniteAnimationSpec<Float> = when (preset) {
    MotionPreset.EMIL -> tween(durationMillis = 360, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
    MotionPreset.SNAPPY -> tween(durationMillis = 110, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    MotionPreset.ELASTIC -> spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow)
    MotionPreset.EDITORIAL -> tween(durationMillis = 620, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
    MotionPreset.REDUCED -> tween(durationMillis = 0)
}

fun motionOffsetSpecFor(preset: MotionPreset): FiniteAnimationSpec<IntOffset> = when (preset) {
    MotionPreset.EMIL -> tween(durationMillis = 340, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
    MotionPreset.SNAPPY -> tween(durationMillis = 110, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    MotionPreset.ELASTIC -> spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessMediumLow)
    MotionPreset.EDITORIAL -> tween(durationMillis = 600, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
    MotionPreset.REDUCED -> tween(durationMillis = 0)
}

fun pressScaleFor(preset: MotionPreset): Float = when (preset) {
    MotionPreset.ELASTIC -> 0.93f
    MotionPreset.REDUCED -> 1f
    MotionPreset.SNAPPY -> 0.98f
    else -> 0.96f
}

data class BusRouteCardData(
    val leg1: String,
    val leg2: String,
    val via: String,
    val stopsSummary: String,
    val freq: String,
    val hub: String,
    val leg1Stops: Int,
    val leg2Stops: Int,
    val stopNames: List<String>
)

data class PlaceListItem(
    val badge: String,
    val isOrange: Boolean,
    val title: String,
    val subtitle: String,
    val tag: String,
    val mode: TransitMode,
    val isNamedPlace: Boolean = true,
    val coordsLabel: String = "13.0846° N, 77.4847° E",
    val distanceLabel: String = "25.0 km"
)

object PreviewFixtures {
    val metroStopsSample = listOf(
        "Singayyanapalya (Board)",
        "KR Pura",
        "Benniganahalli",
        "Baiyappanahalli",
        "Swami Vivekananda Rd",
        "Indiranagar",
        "Halasuru",
        "Trinity",
        "MG Road",
        "Cubbon Park",
        "Vidhana Soudha",
        "Central College",
        "Majestic (Change to Green Line)",
        "Mantri Square Sampige Rd",
        "Srirampura",
        "Rajajinagar",
        "Mahalakshmi",
        "Sandal Soap Factory",
        "Yeshwanthpur",
        "Goraguntepalya",
        "Peenya",
        "Peenya Industry",
        "Jalahalli",
        "Dasarahalli",
        "Nagasandra",
        "Chikkabidarakallu (Alert Stop)",
        "Madavara (Exit)"
    )

    val busOptions = listOf(
        BusRouteCardData(
            leg1 = "KBS-12HK", leg2 = "253-J", via = "Via Majestic", stopsSummary = "34 stops",
            freq = "Every ~9m • High Freq", hub = "Majestic KBS", leg1Stops = 18, leg2Stops = 16,
            stopNames = listOf("Singaianapalya Phoenix (Board)", "KR Pura Railway Stn", "Tin Factory", "Indiranagar", "Ulsoor", "MG Road", "Corporation", "Majestic KBS (Transfer to 253-J)", "Yeshwanthpur TTMC", "Jalahalli Cross", "Peenya 2nd Stage", "Thammenahalli Cross (Alert)", "Acharya Institute (Exit)")
        ),
        BusRouteCardData(
            leg1 = "507-B", leg2 = "250-B", via = "Via CMTI", stopsSummary = "42 stops",
            freq = "Every ~25m • Sarige", hub = "CMTI", leg1Stops = 27, leg2Stops = 15,
            stopNames = listOf("Singaianapalya Phoenix (Board)", "KR Pura", "Hebbal Flyover", "BEL Circle", "CMTI (Transfer to 250-B)", "Jalahalli Cross", "Achyuthanagara Gate (Alert)", "Acharya Institute (Exit)")
        ),
        BusRouteCardData(
            leg1 = "507-B", leg2 = "V-KBS-AIT", via = "Via CMTI", stopsSummary = "36 stops",
            freq = "AC Vajra Leg 2", hub = "CMTI", leg1Stops = 27, leg2Stops = 9,
            stopNames = listOf("Singaianapalya Phoenix (Board)", "KR Pura", "Hebbal", "CMTI (Transfer to V-KBS-AIT)", "Peenya", "Achyuthanagara Gate (Alert)", "Acharya Institute (Exit)")
        )
    )

    val savedPlaces = listOf(
        PlaceListItem("C", true, "Acharya College, Soladevanahalli", "Metro • 1 stop before exit", "25.0 km", TransitMode.METRO, true, "13.0846° N, 77.4847° E", "25.0 km"),
        PlaceListItem("P", false, "12.9970° N, 77.6970° E", "Unnamed Map Pin • Whitefield", "0.4 km", TransitMode.BUS, false, "12.9970° N, 77.6970° E", "0.4 km"),
        PlaceListItem("W", false, "Bagmane Tech Park, CV Raman Nagar", "Direct Road • 1 km alert", "6.8 km", TransitMode.CAR, true, "12.9806° N, 77.6612° E", "6.8 km")
    )

    val historyItems = listOf(
        PlaceListItem("M", false, "Acharya Institute of Technology", "Namma Metro • 27 stops (Purple ➔ Green)", "Today", TransitMode.METRO, true, "13.0846° N, 77.4847° E", "25.0 km"),
        PlaceListItem("B", false, "Kempegowda Int’l Airport (KIA)", "BMTC KIA-8 • Singaianapalya Phoenix", "Yesterday", TransitMode.BUS, true, "13.1986° N, 77.7066° E", "34.2 km"),
        PlaceListItem("R", true, "Indiranagar 100 Ft Rd", "Direct Road • 1 km reminder", "2d ago", TransitMode.CAR, true, "12.9784° N, 77.6408° E", "7.4 km")
    )
}
