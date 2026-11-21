package com.wakeme.app.data.local.transit

import androidx.compose.ui.graphics.Color
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Three distinct BMTC Bus Service Divisions for Bengaluru:
 * 1. AC Buses (Vajra & Vayu Vajra) -> Dark Navy Blue (#0D47A1) + Crisp White
 * 2. Normal Non-AC Buses (Bengaluru Sarige) -> Classic Royal Blue (#1976D2) + Crisp White
 * 3. Electric Buses (Astra EV) -> Light Sky Blue (#03A9F4) + Crisp White
 *
 * Rendered using a 50/50 two-tone blue and white livery on the map and markers.
 * This guarantees zero visual collision with Namma Metro (Purple, Green, Yellow, Pink),
 * and future-proofs against the upcoming Namma Metro Blue Line.
 */
enum class BmtcBusCategory(
    val categoryName: String,
    val shortCode: String,
    val primaryColorInt: Int,
    val accentColorInt: Int = 0xFFFFFFFF.toInt(),
    val description: String
) {
    AC_VOLVO(
        categoryName = "AC Bus (Vajra / Vayu Vajra)",
        shortCode = "AC",
        primaryColorInt = 0xFF0D47A1.toInt(), // Dark Navy Blue
        accentColorInt = 0xFFFFFFFF.toInt(),
        description = "Volvo 8400LE AC & Airport Express"
    ),
    NORMAL_SARIGE(
        categoryName = "Bengaluru Sarige (Non-AC)",
        shortCode = "Sarige",
        primaryColorInt = 0xFF1976D2.toInt(), // Classic Royal Blue
        accentColorInt = 0xFFFFFFFF.toInt(),
        description = "Regular City 50/50 White-over-Blue Bus"
    ),
    ELECTRIC_EV(
        categoryName = "Astra Electric (EV)",
        shortCode = "EV",
        primaryColorInt = 0xFF03A9F4.toInt(), // Light Sky Blue
        accentColorInt = 0xFFFFFFFF.toInt(),
        description = "Zero-Emission Electric Bus"
    );

    val composeColor: Color get() = Color(primaryColorInt)
    val composeAccent: Color get() = Color(accentColorInt)

    companion object {
        fun fromRouteOrName(text: String?): BmtcBusCategory {
            if (text.isNullOrBlank()) return NORMAL_SARIGE
            val upper = text.uppercase()
            return when {
                upper.contains("KIA") || upper.contains("VAJRA") || upper.contains("VAYU") ||
                upper.contains("V-") || upper.contains("AIRPORT") || upper.contains("AC ") -> AC_VOLVO

                upper.contains("EV") || upper.contains("ASTRA") || upper.contains("ELECTRIC") ||
                upper.contains("E-") -> ELECTRIC_EV

                else -> NORMAL_SARIGE
            }
        }
    }
}

data class BmtcMajorHub(
    val id: String,
    val name: String,
    val category: BmtcBusCategory,
    val latitude: Double,
    val longitude: Double,
    val routesSummary: String
) {
    val geoPoint: GeoPoint get() = GeoPoint(latitude, longitude)
}

object BmtcBusNetwork {

    // --- Major BMTC Bus Hubs & TTMCs ---
    val MAJOR_HUBS: List<BmtcMajorHub> = listOf(
        BmtcMajorHub("hub_kbs", "Kempegowda Bus Station (Majestic)", BmtcBusCategory.NORMAL_SARIGE, 12.9775, 77.5714, "City Central Terminal • Sarige & Vajra Hub"),
        BmtcMajorHub("hub_silkboard", "Central Silk Board Junction", BmtcBusCategory.AC_VOLVO, 12.9176, 77.6238, "Outer Ring Road & Hosur Road Junction"),
        BmtcMajorHub("hub_hebbal", "Hebbal Bus Interchange", BmtcBusCategory.AC_VOLVO, 13.0358, 77.5971, "Airport Expressway & Ring Road Hub"),
        BmtcMajorHub("hub_airport", "Kempegowda Int'l Airport (KIA T1/T2)", BmtcBusCategory.AC_VOLVO, 13.1986, 77.7066, "Vayu Vajra Dedicated Airport Terminal"),
        BmtcMajorHub("hub_marathahalli", "Marathahalli Bridge Hub", BmtcBusCategory.AC_VOLVO, 12.9562, 77.7011, "Outer Ring Road & Whitefield Interchange"),
        BmtcMajorHub("hub_itpl", "ITPL Main Gate (Whitefield)", BmtcBusCategory.ELECTRIC_EV, 12.9856, 77.7289, "Astra EV & Tech Park Feeder Terminal"),
        BmtcMajorHub("hub_banashankari", "Banashankari TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.9156, 77.5734, "South Bengaluru Central TTMC"),
        BmtcMajorHub("hub_shantinagar", "Shantinagar TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.9542, 77.5932, "Central City Bus & KSRTC Station"),
        BmtcMajorHub("hub_shivajinagar", "Shivaji Nagar Bus Stand", BmtcBusCategory.NORMAL_SARIGE, 12.9862, 77.6021, "Cantonment & East Bengaluru Central Hub"),
        BmtcMajorHub("hub_yeshwantpur", "Yeshwantpur TTMC", BmtcBusCategory.NORMAL_SARIGE, 13.0234, 77.5512, "North-West Rail & Transit Junction"),
        BmtcMajorHub("hub_jayanagar", "Jayanagar 4th Block TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.9298, 77.5834, "South Bengaluru Shopping & Commuter Hub"),
        BmtcMajorHub("hub_kengeri", "Kengeri TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.9148, 77.4878, "Mysore Road West Entrance TTMC")
    )

    // --- 1. Outer Ring Road AC Volvo Trunk Corridor (Dark Navy Blue & White) ---
    // Silk Board <-> Bellandur <-> Marathahalli <-> KR Puram <-> Hebbal (500-D Series)
    val CORRIDOR_ORR_AC_VOLVO: List<GeoPoint> = listOf(
        GeoPoint(12.9176, 77.6238), // Silk Board
        GeoPoint(12.9116, 77.6388), // HSR Layout
        GeoPoint(12.9234, 77.6521), // Agara Junction
        GeoPoint(12.9272, 77.6685), // Iblur Junction
        GeoPoint(12.9351, 77.6775), // Bellandur
        GeoPoint(12.9431, 77.6892), // Devarabisanahalli
        GeoPoint(12.9472, 77.6953), // Kadubeesanahalli
        GeoPoint(12.9562, 77.7011), // Marathahalli Bridge
        GeoPoint(12.9734, 77.6978), // Karthik Nagar
        GeoPoint(12.9881, 77.6892), // Mahadevapura
        GeoPoint(13.0012, 77.6745), // KR Puram / Tin Factory
        GeoPoint(13.0165, 77.6632), // Ramamurthy Nagar
        GeoPoint(13.0232, 77.6412), // Kalyan Nagar
        GeoPoint(13.0378, 77.6201), // Nagawara
        GeoPoint(13.0358, 77.5971)  // Hebbal Flyover
    )

    // --- 2. Airport Vayu Vajra Express Corridor (Dark Navy Blue & White) ---
    // Majestic KBS <-> Mekhri Circle <-> Hebbal <-> Yelahanka <-> Airport (KIA-8 / KIA-9)
    val CORRIDOR_AIRPORT_VAYU_VAJRA: List<GeoPoint> = listOf(
        GeoPoint(12.9775, 77.5714), // Majestic KBS
        GeoPoint(12.9811, 77.5750), // Ananda Rao Circle
        GeoPoint(12.9984, 77.5812), // Cauvery Theatre
        GeoPoint(13.0105, 77.5856), // Mekhri Circle
        GeoPoint(13.0245, 77.5898), // Sanjay Nagar Cross
        GeoPoint(13.0358, 77.5971), // Hebbal Flyover
        GeoPoint(13.0567, 77.5956), // Kodigehalli Gate
        GeoPoint(13.0782, 77.6012), // Jakkur Cross
        GeoPoint(13.1012, 77.5982), // Yelahanka Bypass
        GeoPoint(13.1234, 77.6189), // Kogilu Cross
        GeoPoint(13.1678, 77.6345), // Chikkajala
        GeoPoint(13.1991, 77.6756), // Trumpet Interchange
        GeoPoint(13.1986, 77.7066)  // Kempegowda Int'l Airport
    )

    // --- 3. Central City Sarige Non-AC Trunk Line (Classic Royal Blue & White) ---
    // Majestic <-> Corporation <-> Lalbagh <-> Jayanagar <-> Silk Board
    val CORRIDOR_CITY_SARIGE: List<GeoPoint> = listOf(
        GeoPoint(12.9775, 77.5714), // Majestic KBS
        GeoPoint(12.9745, 77.5802), // Mysore Bank Circle
        GeoPoint(12.9698, 77.5872), // Hudson Circle / Corporation
        GeoPoint(12.9567, 77.5862), // Lalbagh Main Gate
        GeoPoint(12.9378, 77.5812), // South End Circle
        GeoPoint(12.9298, 77.5834), // Jayanagar 4th Block
        GeoPoint(12.9167, 77.6045), // BTM Layout Water Tank
        GeoPoint(12.9176, 77.6238)  // Central Silk Board
    )

    // --- 4. East-West Astra Electric EV Trunk Corridor (Light Sky Blue & White) ---
    // Majestic <-> MG Road <-> Indiranagar <-> HAL <-> Marathahalli <-> ITPL
    val CORRIDOR_EAST_WEST_EV: List<GeoPoint> = listOf(
        GeoPoint(12.9775, 77.5714), // Majestic KBS
        GeoPoint(12.9667, 77.5982), // Richmond Circle
        GeoPoint(12.9730, 77.6170), // Trinity / MG Road
        GeoPoint(12.9608, 77.6412), // Domlur Flyover
        GeoPoint(12.9578, 77.6712), // HAL Main Gate
        GeoPoint(12.9562, 77.7011), // Marathahalli Bridge
        GeoPoint(12.9632, 77.7156), // Kundalahalli Gate
        GeoPoint(12.9782, 77.7104), // Graphite India
        GeoPoint(12.9856, 77.7289)  // ITPL Whitefield
    )

    fun findNearestHub(point: GeoPoint): Pair<BmtcMajorHub, Double> {
        var minDistance = Double.MAX_VALUE
        var nearestHub = MAJOR_HUBS.first()

        for (hub in MAJOR_HUBS) {
            val dist = haversineDistance(
                point.latitude, point.longitude,
                hub.latitude, hub.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
                nearestHub = hub
            }
        }
        return Pair(nearestHub, minDistance)
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
