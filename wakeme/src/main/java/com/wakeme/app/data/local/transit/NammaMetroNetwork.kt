package com.wakeme.app.data.local.transit

import org.osmdroid.util.GeoPoint

/**
 * Authentic Namma Metro (BMRCL) track coordinates and line color definitions.
 * Includes operational Purple, Green, Yellow lines, and the newly launched Pink Line.
 */
object NammaMetroNetwork {

    const val COLOR_PURPLE = 0xFF8E24AA.toInt()  // Deep BMRCL Purple
    const val COLOR_GREEN = 0xFF2E7D32.toInt()   // BMRCL Emerald Green
    const val COLOR_YELLOW = 0xFFF57F17.toInt()  // BMRCL Golden Amber
    const val COLOR_PINK = 0xFFD81B60.toInt()    // BMRCL Vibrant Rose Pink
    const val COLOR_INTERCHANGE = 0xFF0D47A1.toInt() // Multi-line Transit Interchange

    val TRACK_PURPLE: List<GeoPoint> = listOf(
        GeoPoint(12.897354, 77.461288),
        GeoPoint(12.907993, 77.476524),
        GeoPoint(12.914788, 77.487773),
        GeoPoint(12.924302, 77.49828),
        GeoPoint(12.93542, 77.512177),
        GeoPoint(12.9366, 77.519679),
        GeoPoint(12.941671, 77.525117),
        GeoPoint(12.946718, 77.530159),
        GeoPoint(12.961893, 77.533579),
        GeoPoint(12.952058, 77.537012),
        GeoPoint(12.970956, 77.537404),
        GeoPoint(12.974293, 77.545621),
        GeoPoint(12.975632, 77.555352),
        GeoPoint(12.975877, 77.565377),
        GeoPoint(12.975708, 77.572876),
        GeoPoint(12.97452, 77.58422),
        GeoPoint(12.978742, 77.591639),
        GeoPoint(12.980958, 77.597576),
        GeoPoint(12.975526, 77.60679),
        GeoPoint(12.973022, 77.61702),
        GeoPoint(12.976499, 77.626686),
        GeoPoint(12.978333, 77.638661),
        GeoPoint(12.985931, 77.644897),
        GeoPoint(12.990759, 77.652361),
        GeoPoint(12.996496, 77.668479),
        GeoPoint(12.999902, 77.67767),
        GeoPoint(12.996545, 77.692718),
        GeoPoint(12.99345, 77.703677),
        GeoPoint(12.980856, 77.708785),
        GeoPoint(12.988803, 77.711326),
        GeoPoint(12.977594, 77.715559),
        GeoPoint(12.976641, 77.724885),
        GeoPoint(12.981195, 77.727536),
        GeoPoint(12.987639, 77.737772),
        GeoPoint(12.98565, 77.747012),
        GeoPoint(12.987297, 77.753884),
        GeoPoint(12.995743, 77.757949)
    )

    val TRACK_GREEN: List<GeoPoint> = listOf(
        GeoPoint(13.057421, 77.472806),
        GeoPoint(13.052362, 77.487915),
        GeoPoint(13.05009, 77.494446),
        GeoPoint(13.047954, 77.500142),
        GeoPoint(13.043261, 77.512553),
        GeoPoint(13.03941, 77.519735),
        GeoPoint(13.036318, 77.525492),
        GeoPoint(13.033019, 77.533201),
        GeoPoint(13.028332, 77.540848),
        GeoPoint(13.023268, 77.549875),
        GeoPoint(13.014654, 77.553984),
        GeoPoint(13.008047, 77.548807),
        GeoPoint(13.000525, 77.549657),
        GeoPoint(12.99853, 77.556899),
        GeoPoint(12.996525, 77.563196),
        GeoPoint(12.990463, 77.570729),
        GeoPoint(12.975708, 77.572876),
        GeoPoint(12.966897, 77.574557),
        GeoPoint(12.960879, 77.574658),
        GeoPoint(12.950527, 77.57369),
        GeoPoint(12.946526, 77.580016),
        GeoPoint(12.938257, 77.580056),
        GeoPoint(12.929507, 77.580144),
        GeoPoint(12.921581, 77.580202),
        GeoPoint(12.915221, 77.573598),
        GeoPoint(12.907475, 77.573128),
        GeoPoint(12.89605, 77.570119),
        GeoPoint(12.888967, 77.562667),
        GeoPoint(12.884643, 77.552755),
        GeoPoint(12.877437, 77.544741),
        GeoPoint(12.87141, 77.538396),
        GeoPoint(12.86173, 77.529955)
    )

    val TRACK_YELLOW: List<GeoPoint> = listOf(
        GeoPoint(12.921581, 77.580202),
        GeoPoint(12.917081, 77.588302),
        GeoPoint(12.916731, 77.599966),
        GeoPoint(12.916588, 77.608118),
        GeoPoint(12.916582, 77.620567),
        GeoPoint(12.910616, 77.626478),
        GeoPoint(12.901695, 77.631986),
        GeoPoint(12.889926, 77.639216),
        GeoPoint(12.880818, 77.644846),
        GeoPoint(12.870749, 77.652447),
        GeoPoint(12.863878, 77.657904),
        GeoPoint(12.856505, 77.663523),
        GeoPoint(12.84644, 77.671187),
        GeoPoint(12.839157, 77.677372),
        GeoPoint(12.829033, 77.681374),
        GeoPoint(12.819394, 77.688345)
    )

    val TRACK_PINK: List<GeoPoint> = listOf(
        GeoPoint(12.871209, 77.59363),
        GeoPoint(12.8804, 77.595821),
        GeoPoint(12.895384, 77.599531),
        GeoPoint(12.909433, 77.600221),
        GeoPoint(12.909848, 77.600091),
        GeoPoint(12.929015, 77.600542),
        GeoPoint(12.941223, 77.602429),
        GeoPoint(12.949486, 77.603035),
        GeoPoint(12.956231, 77.605864),
        GeoPoint(12.965276, 77.606948),
        GeoPoint(12.976235, 77.608381),
        GeoPoint(12.983849, 77.602035),
        GeoPoint(12.991793, 77.60622),
        GeoPoint(13.000652, 77.609432),
        GeoPoint(13.00959, 77.613949),
        GeoPoint(13.017306, 77.618018),
        GeoPoint(13.029958, 77.620727),
        GeoPoint(13.041216, 77.62502)
    )

    fun getLineColor(lineName: String?): Int {
        if (lineName == null) return COLOR_PURPLE
        val lower = lineName.lowercase()
        return when {
            lower.contains("purple") && (lower.contains("green") || lower.contains("yellow") || lower.contains("pink")) -> COLOR_INTERCHANGE
            lower.contains("green") && (lower.contains("yellow") || lower.contains("pink")) -> COLOR_INTERCHANGE
            lower.contains("purple") -> COLOR_PURPLE
            lower.contains("green") -> COLOR_GREEN
            lower.contains("yellow") -> COLOR_YELLOW
            lower.contains("pink") -> COLOR_PINK
            else -> COLOR_PURPLE
        }
    }
}
