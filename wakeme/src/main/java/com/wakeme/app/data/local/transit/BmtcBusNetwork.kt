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

data class ActiveBusStop(
    val id: String,
    val name: String,
    val category: BmtcBusCategory,
    val latitude: Double,
    val longitude: Double,
    val routesSummary: String = "BMTC Bus Stop",
    val isMajorHub: Boolean = false
) {
    val geoPoint: GeoPoint get() = GeoPoint(latitude, longitude)
}

object BmtcBusNetwork {

    // =========================================================================
    // 1. CORRIDOR 1: Outer Ring Road AC Volvo Trunk (Dark Navy Blue + White)
    // 500-D Series: Central Silk Board <-> Marathahalli <-> KR Puram <-> Hebbal
    // =========================================================================
    val TRACK_ORR_AC_VOLVO: List<GeoPoint> = listOf(
        GeoPoint(12.91734, 77.62275), // Central Silk Board
        GeoPoint(12.91282, 77.63805), // HSR Layout BDA Complex
        GeoPoint(12.92437, 77.65047), // Agara Junction
        GeoPoint(12.92720, 77.66850), // Iblur Junction
        GeoPoint(12.92807, 77.68085), // Bellandur / Eco Space
        GeoPoint(12.93510, 77.68920), // Devarabisanahalli
        GeoPoint(12.94310, 77.69530), // Kadubeesanahalli
        GeoPoint(12.95693, 77.70126), // Marathahalli Bridge
        GeoPoint(12.97532, 77.69732), // Karthik Nagar / Dodda Nekkundi
        GeoPoint(12.98846, 77.68927), // Mahadevapura Ring Road
        GeoPoint(13.00010, 77.67625), // Tin Factory / KR Puram Station
        GeoPoint(13.01462, 77.66528), // Ramamurthy Nagar Ring Road
        GeoPoint(13.02384, 77.64329), // Kalyan Nagar / HRBR Layout
        GeoPoint(13.03731, 77.62335), // Nagawara Ring Road
        GeoPoint(13.03577, 77.58898)  // Hebbal Bus Interchange
    )

    val STOPS_ORR_AC_VOLVO: List<ActiveBusStop> = listOf(
        ActiveBusStop("orr_1", "Central Silk Board", BmtcBusCategory.AC_VOLVO, 12.91734, 77.62275, "Routes: 500-D, 500-A, 356, KIA-14", isMajorHub = true),
        ActiveBusStop("orr_2", "HSR Layout BDA Complex", BmtcBusCategory.AC_VOLVO, 12.91282, 77.63805, "Routes: 500-D, 500-A, V-500"),
        ActiveBusStop("orr_3", "Agara Junction", BmtcBusCategory.AC_VOLVO, 12.92437, 77.65047, "Routes: 500-D, 500-QK, KIA-8"),
        ActiveBusStop("orr_4", "Iblur Junction", BmtcBusCategory.AC_VOLVO, 12.92720, 77.66850, "Routes: 500-D, KIA-8, V-500"),
        ActiveBusStop("orr_5", "Bellandur / Eco Space", BmtcBusCategory.AC_VOLVO, 12.92807, 77.68085, "Routes: 500-D, KIA-8, MF-1", isMajorHub = true),
        ActiveBusStop("orr_6", "Devarabisanahalli", BmtcBusCategory.AC_VOLVO, 12.93510, 77.68920, "Routes: 500-D, 500-A, KIA-8"),
        ActiveBusStop("orr_7", "Kadubeesanahalli", BmtcBusCategory.AC_VOLVO, 12.94310, 77.69530, "Routes: 500-D, KIA-8, V-500"),
        ActiveBusStop("orr_8", "Marathahalli Bridge", BmtcBusCategory.AC_VOLVO, 12.95693, 77.70126, "Routes: 500-D, 335-E, KIA-8, KIA-4", isMajorHub = true),
        ActiveBusStop("orr_9", "Karthik Nagar", BmtcBusCategory.AC_VOLVO, 12.97532, 77.69732, "Routes: 500-D, KIA-8, V-500"),
        ActiveBusStop("orr_10", "Mahadevapura Ring Road", BmtcBusCategory.AC_VOLVO, 12.98846, 77.68927, "Routes: 500-D, 500-F, KIA-8"),
        ActiveBusStop("orr_10a", "B Narayanapura (North)", BmtcBusCategory.AC_VOLVO, 12.99589, 77.68387, "Towards Tin Factory / Hebbal", isMajorHub = true),
        ActiveBusStop("orr_10b", "B Narayanapura (South)", BmtcBusCategory.AC_VOLVO, 12.99583, 77.68417, "Towards Marathahalli / Silk Board", isMajorHub = true),
        ActiveBusStop("orr_11", "Tin Factory / KR Puram", BmtcBusCategory.AC_VOLVO, 13.00010, 77.67625, "Routes: 500-D, 314, KIA-8, V-500", isMajorHub = true),
        ActiveBusStop("orr_12", "Ramamurthy Nagar", BmtcBusCategory.AC_VOLVO, 13.01462, 77.66528, "Routes: 500-D, 500-B, KIA-8"),
        ActiveBusStop("orr_13", "Kalyan Nagar", BmtcBusCategory.AC_VOLVO, 13.02384, 77.64329, "Routes: 500-D, KIA-8, V-500"),
        ActiveBusStop("orr_14", "Nagawara Ring Road", BmtcBusCategory.AC_VOLVO, 13.03731, 77.62335, "Routes: 500-D, KIA-8, V-500"),
        ActiveBusStop("orr_15", "Hebbal Bus Interchange", BmtcBusCategory.AC_VOLVO, 13.03577, 77.58898, "Routes: 500-D, KIA-9, KIA-8, 280", isMajorHub = true)
    )

    // =========================================================================
    // 2. CORRIDOR 2: Airport Vayu Vajra Express Corridor (Dark Navy Blue + White)
    // KIA-8 / KIA-9: Majestic KBS <-> Mekhri Circle <-> Hebbal <-> Airport
    // =========================================================================
    val TRACK_AIRPORT_VAYU_VAJRA: List<GeoPoint> = listOf(
        GeoPoint(12.97751, 77.57141), // Majestic KBS
        GeoPoint(12.98095, 77.57484), // Ananda Rao Circle
        GeoPoint(12.99841, 77.58207), // Palace Guttahalli
        GeoPoint(13.01059, 77.58388), // Mekhri Circle
        GeoPoint(13.02626, 77.58907), // Sanjay Nagar Cross
        GeoPoint(13.03577, 77.58898), // Hebbal Flyover
        GeoPoint(13.05578, 77.59431), // Kodigehalli Gate
        GeoPoint(13.07756, 77.60618), // Jakkur Cross
        GeoPoint(13.09884, 77.59705), // Yelahanka Bypass
        GeoPoint(13.12139, 77.61850), // Kogilu Cross
        GeoPoint(13.16830, 77.63139), // Chikkajala
        GeoPoint(13.19814, 77.68004), // Trumpet Interchange
        GeoPoint(13.19910, 77.70822)  // Kempegowda International Airport (KIA T1/T2)
    )

    val STOPS_AIRPORT_VAYU_VAJRA: List<ActiveBusStop> = listOf(
        ActiveBusStop("kia_1", "Kempegowda Bus Station (Majestic)", BmtcBusCategory.AC_VOLVO, 12.97751, 77.57141, "Routes: KIA-9, KIA-10, 250, Sarige Central", isMajorHub = true),
        ActiveBusStop("kia_2", "Ananda Rao Circle", BmtcBusCategory.AC_VOLVO, 12.98095, 77.57484, "Routes: KIA-9, 250, 252"),
        ActiveBusStop("kia_3", "Palace Guttahalli", BmtcBusCategory.AC_VOLVO, 12.99841, 77.58207, "Routes: KIA-9, 276, 280"),
        ActiveBusStop("kia_4", "Mekhri Circle", BmtcBusCategory.AC_VOLVO, 13.01059, 77.58388, "Routes: KIA-9, KIA-8, 280", isMajorHub = true),
        ActiveBusStop("kia_5", "Sanjay Nagar Cross", BmtcBusCategory.AC_VOLVO, 13.02626, 77.58907, "Routes: KIA-9, 280, 282"),
        ActiveBusStop("kia_6", "Kodigehalli Gate", BmtcBusCategory.AC_VOLVO, 13.05578, 77.59431, "Routes: KIA-9, 285"),
        ActiveBusStop("kia_7", "Jakkur Cross", BmtcBusCategory.AC_VOLVO, 13.07756, 77.60618, "Routes: KIA-9, 285-M"),
        ActiveBusStop("kia_8", "Yelahanka Santhe Circle", BmtcBusCategory.AC_VOLVO, 13.09884, 77.59705, "Routes: KIA-9, 285, 401", isMajorHub = true),
        ActiveBusStop("kia_9", "Kogilu Cross", BmtcBusCategory.AC_VOLVO, 13.12139, 77.61850, "Routes: KIA-9, KIA-8"),
        ActiveBusStop("kia_10", "Chikkajala", BmtcBusCategory.AC_VOLVO, 13.16830, 77.63139, "Routes: KIA-9, 298"),
        ActiveBusStop("kia_11", "Trumpet Interchange", BmtcBusCategory.AC_VOLVO, 13.19814, 77.68004, "Routes: KIA-9, KIA-8"),
        ActiveBusStop("kia_12", "Kempegowda Int'l Airport", BmtcBusCategory.AC_VOLVO, 13.19910, 77.70822, "Vayu Vajra Dedicated Airport Terminal", isMajorHub = true)
    )

    // =========================================================================
    // 3. CORRIDOR 3: Central City Sarige Non-AC Trunk Line (Classic Royal Blue + White)
    // 201 Series: Majestic <-> Corporation <-> Lalbagh <-> Jayanagar <-> Silk Board
    // =========================================================================
    val TRACK_CITY_SARIGE: List<GeoPoint> = listOf(
        GeoPoint(12.97751, 77.57141), // Majestic KBS
        GeoPoint(12.97346, 77.58210), // Mysore Bank Circle
        GeoPoint(12.96945, 77.58716), // Hudson Circle / Corporation
        GeoPoint(12.95417, 77.58501), // Lalbagh Main Gate
        GeoPoint(12.93827, 77.58024), // South End Circle
        GeoPoint(12.92863, 77.58448), // Jayanagar 4th Block
        GeoPoint(12.91657, 77.60427), // BTM Layout Water Tank
        GeoPoint(12.91734, 77.62275)  // Central Silk Board
    )

    val STOPS_CITY_SARIGE: List<ActiveBusStop> = listOf(
        ActiveBusStop("sarige_1", "Kempegowda Bus Station (Majestic)", BmtcBusCategory.NORMAL_SARIGE, 12.97751, 77.57141, "Sarige City Bus Central Hub", isMajorHub = true),
        ActiveBusStop("sarige_2", "Mysore Bank Circle", BmtcBusCategory.NORMAL_SARIGE, 12.97346, 77.58210, "Routes: 201, 215, 210"),
        ActiveBusStop("sarige_3", "Hudson Circle / Corporation", BmtcBusCategory.NORMAL_SARIGE, 12.96945, 77.58716, "Routes: 201, 215, 342", isMajorHub = true),
        ActiveBusStop("sarige_4", "Lalbagh Main Gate", BmtcBusCategory.NORMAL_SARIGE, 12.95417, 77.58501, "Routes: 201, 215, 365"),
        ActiveBusStop("sarige_5", "South End Circle", BmtcBusCategory.NORMAL_SARIGE, 12.93827, 77.58024, "Routes: 201, 215, South End Circle Metro"),
        ActiveBusStop("sarige_6", "Jayanagar 4th Block", BmtcBusCategory.NORMAL_SARIGE, 12.92863, 77.58448, "Routes: 201, 215, 18, 25", isMajorHub = true),
        ActiveBusStop("sarige_7", "BTM Layout 16th Main", BmtcBusCategory.NORMAL_SARIGE, 12.91657, 77.60427, "Routes: 201, 201-R, 356"),
        ActiveBusStop("sarige_8", "Central Silk Board", BmtcBusCategory.NORMAL_SARIGE, 12.91734, 77.62275, "Sarige & Vajra Major Transit Junction", isMajorHub = true)
    )

    // =========================================================================
    // 4. CORRIDOR 4: East-West Astra Electric EV Trunk Corridor (Light Sky Blue + White)
    // 335-E EV: Majestic <-> MG Road <-> Indiranagar <-> HAL <-> Marathahalli <-> ITPL
    // =========================================================================
    val TRACK_EAST_WEST_EV: List<GeoPoint> = listOf(
        GeoPoint(12.97751, 77.57141), // Majestic KBS
        GeoPoint(12.96638, 77.59838), // Richmond Circle
        GeoPoint(12.97305, 77.61687), // Trinity / MG Road
        GeoPoint(12.96051, 77.64253), // Domlur Flyover
        GeoPoint(12.95807, 77.66939), // HAL Main Gate
        GeoPoint(12.95693, 77.70126), // Marathahalli Bridge
        GeoPoint(12.96068, 77.71645), // Kundalahalli Gate
        GeoPoint(12.97787, 77.71029), // Graphite India
        GeoPoint(12.98747, 77.73155)  // ITPL Whitefield
    )

    val STOPS_EAST_WEST_EV: List<ActiveBusStop> = listOf(
        ActiveBusStop("ev_1", "Kempegowda Bus Station (Majestic)", BmtcBusCategory.ELECTRIC_EV, 12.97751, 77.57141, "Astra EV 335-E Terminus", isMajorHub = true),
        ActiveBusStop("ev_2", "Richmond Circle", BmtcBusCategory.ELECTRIC_EV, 12.96638, 77.59838, "Routes: 335-E, 333-E, 141"),
        ActiveBusStop("ev_3", "Trinity Circle / MG Road", BmtcBusCategory.ELECTRIC_EV, 12.97305, 77.61687, "Routes: 335-E, 333, Trinity Metro Station", isMajorHub = true),
        ActiveBusStop("ev_4", "Domlur Flyover", BmtcBusCategory.ELECTRIC_EV, 12.96051, 77.64253, "Routes: 335-E, Domlur TTMC", isMajorHub = true),
        ActiveBusStop("ev_5", "HAL Main Gate", BmtcBusCategory.ELECTRIC_EV, 12.95807, 77.66939, "Routes: 335-E, 333-P, 330"),
        ActiveBusStop("ev_6", "Marathahalli Bridge", BmtcBusCategory.ELECTRIC_EV, 12.95693, 77.70126, "Routes: 335-E, 500-D, KIA-8", isMajorHub = true),
        ActiveBusStop("ev_7", "Kundalahalli Gate", BmtcBusCategory.ELECTRIC_EV, 12.96068, 77.71645, "Routes: 335-E, 333-E"),
        ActiveBusStop("ev_8", "Graphite India", BmtcBusCategory.ELECTRIC_EV, 12.97787, 77.71029, "Routes: 335-E, 500-D EV"),
        ActiveBusStop("ev_9", "ITPL Main Gate (Whitefield)", BmtcBusCategory.ELECTRIC_EV, 12.98747, 77.73155, "Routes: 335-E, MF-5, Tech Park Feeder", isMajorHub = true)
    )

    // Additional Major City Transit Terminals & TTMCs
    val ADDITIONAL_MAJOR_HUBS: List<ActiveBusStop> = listOf(
        ActiveBusStop("hub_bsk", "Banashankari TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.91560, 77.57340, "South Bengaluru Central TTMC • Green Line Metro Interchange", isMajorHub = true),
        ActiveBusStop("hub_shanti", "Shantinagar TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.95420, 77.59320, "Central City Bus & KSRTC Station", isMajorHub = true),
        ActiveBusStop("hub_shivaji", "Shivaji Nagar Bus Stand", BmtcBusCategory.NORMAL_SARIGE, 12.98620, 77.60210, "Cantonment & East Bengaluru Central Hub", isMajorHub = true),
        ActiveBusStop("hub_yesh", "Yeshwantpur TTMC", BmtcBusCategory.NORMAL_SARIGE, 13.02340, 77.55120, "North-West Rail & Transit Junction", isMajorHub = true),
        ActiveBusStop("hub_kengeri", "Kengeri TTMC", BmtcBusCategory.NORMAL_SARIGE, 12.91480, 77.48780, "Mysore Road West Entrance TTMC", isMajorHub = true),
        ActiveBusStop("hub_wf", "Whitefield TTMC", BmtcBusCategory.ELECTRIC_EV, 12.96820, 77.74920, "Whitefield Commuter Bus Terminal", isMajorHub = true)
    )

    /**
     * Complete in-memory master collection of all prominent bus stops along the corridors + major hubs.
     * Guaranteed to load with 0ms latency, zero SQLite locks, and full high-contrast name pill markings!
     */
    val ALL_PROMINENT_BUS_STOPS: List<ActiveBusStop> by lazy {
        val list = mutableListOf<ActiveBusStop>()
        val seen = mutableSetOf<String>()

        fun addIfNew(stop: ActiveBusStop) {
            val key = "${stop.name.lowercase().trim()}_${stop.latitude}_${stop.longitude}"
            if (key !in seen) {
                seen.add(key)
                list.add(stop)
            }
        }

        STOPS_ORR_AC_VOLVO.forEach { addIfNew(it) }
        STOPS_AIRPORT_VAYU_VAJRA.forEach { addIfNew(it) }
        STOPS_CITY_SARIGE.forEach { addIfNew(it) }
        STOPS_EAST_WEST_EV.forEach { addIfNew(it) }
        ADDITIONAL_MAJOR_HUBS.forEach { addIfNew(it) }
        list
    }

    fun findNearestStop(point: GeoPoint): Pair<ActiveBusStop, Double> {
        var minDistance = Double.MAX_VALUE
        var nearestStop = ALL_PROMINENT_BUS_STOPS.first()

        for (stop in ALL_PROMINENT_BUS_STOPS) {
            val dist = haversineDistance(
                point.latitude, point.longitude,
                stop.latitude, stop.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
                nearestStop = stop
            }
        }
        return Pair(nearestStop, minDistance)
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
