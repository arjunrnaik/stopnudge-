package com.wakeme.uipreview

import org.osmdroid.util.GeoPoint

enum class MetroLineColor { PURPLE, GREEN, YELLOW, PINK, INTERCHANGE, BUS }

data class StopCoord(
    val name: String,
    val lat: Double,
    val lng: Double,
    val line: MetroLineColor = MetroLineColor.BUS
) {
    val geoPoint: GeoPoint get() = GeoPoint(lat, lng)
}

data class MetroScenarioLeg(
    val lineName: String,
    val lineColor: MetroLineColor,
    val stops: List<StopCoord>
)

data class MetroScenarioPlan(
    val boardStation: StopCoord,
    val boardWalkMeters: Int,
    val boardWalkMins: Int,
    val exitStation: StopCoord,
    val exitRoadLabel: String,
    val exitDistLabel: String,
    val exitMins: Int,
    val legs: List<MetroScenarioLeg>
) {
    val allStops: List<StopCoord>
        get() = buildList {
            legs.forEachIndexed { idx, leg ->
                if (idx == 0) addAll(leg.stops)
                else addAll(leg.stops.drop(1))
            }
        }

    val totalStopsCount: Int get() = (allStops.size - 1).coerceAtLeast(1)

    val summaryLine: String
        get() = legs.joinToString(" ➔ ") { it.lineName }
}

data class BusScenarioLeg(
    val primaryRoute: String,
    val sharedRoutes: List<String> = emptyList(),
    val stopsCount: Int,
    val boardStop: String,
    val exitStop: String
)

data class BusScenarioOption(
    val legs: List<BusScenarioLeg>,
    val via: String,
    val stopsSummary: String,
    val freq: String,
    val hub: String,
    val boardWalkMeters: Int,
    val boardWalkMins: Int,
    val exitWalkMeters: Int,
    val exitWalkMins: Int,
    val stops: List<StopCoord>
) {
    val chainSummary: String get() = legs.joinToString(" › ") { it.primaryRoute }
    val leg1: String get() = legs.first().primaryRoute
}

data class CarScenarioPlan(
    val roadPoints: List<GeoPoint>,
    val roadDistLabel: String,
    val aerialDistLabel: String,
    val etaLabel: String,
    val viaLabel: String
)

data class TripScenario(
    val id: String,
    val badge: String,
    val isOrangeBadge: Boolean,
    val originName: String,
    val originPoint: GeoPoint,
    val destName: String,
    val destPoint: GeoPoint,
    val isNamedPlace: Boolean,
    val coordsLabel: String,
    val aerialDistanceLabel: String,
    val tag: String,
    val defaultMode: TransitMode,
    val metroPlan: MetroScenarioPlan?,
    val busOptions: List<BusScenarioOption>,
    val carPlan: CarScenarioPlan
)

object ScenarioFixtures {

    // Core Bengaluru Metro & Transit Coordinates
    private val sSingayyanapalya = StopCoord("Singayyanapalya", 12.9965, 77.6928, MetroLineColor.PURPLE)
    private val sKrPura = StopCoord("KR Pura", 13.0002, 77.6775, MetroLineColor.PURPLE)
    private val sBaiyappanahalli = StopCoord("Baiyappanahalli", 12.9907, 77.6525, MetroLineColor.PURPLE)
    private val sIndiranagar = StopCoord("Indiranagar", 12.9784, 77.6386, MetroLineColor.PURPLE)
    private val sMgRoad = StopCoord("MG Road", 12.9756, 77.6066, MetroLineColor.PURPLE)
    private val sCubbonPark = StopCoord("Cubbon Park", 12.9809, 77.5975, MetroLineColor.PURPLE)
    private val sMajestic = StopCoord("Majestic (Interchange)", 12.9756, 77.5728, MetroLineColor.INTERCHANGE)
    private val sYeshwanthpur = StopCoord("Yeshwanthpur", 13.0238, 77.5504, MetroLineColor.GREEN)
    private val sPeenya = StopCoord("Peenya", 13.0329, 77.5335, MetroLineColor.GREEN)
    private val sJalahalli = StopCoord("Jalahalli", 13.0394, 77.5197, MetroLineColor.GREEN)
    private val sNagasandra = StopCoord("Nagasandra", 13.0478, 77.5003, MetroLineColor.GREEN)
    private val sMadavara = StopCoord("Madavara", 13.0598, 77.4876, MetroLineColor.GREEN)
    private val sChickpet = StopCoord("Chickpet", 12.9669, 77.5746, MetroLineColor.GREEN)
    private val sNationalCollege = StopCoord("National College", 12.9506, 77.5736, MetroLineColor.GREEN)
    private val sJayanagar = StopCoord("Jayanagar", 12.9295, 77.5801, MetroLineColor.GREEN)
    private val sRvRoad = StopCoord("RV Road (Interchange)", 12.9214, 77.5802, MetroLineColor.INTERCHANGE)
    private val sBanashankari = StopCoord("Banashankari", 12.9154, 77.5736, MetroLineColor.GREEN)
    private val sSilkInstitute = StopCoord("Silk Institute", 12.8617, 77.5300, MetroLineColor.GREEN)
    private val sSilkBoard = StopCoord("Central Silk Board", 12.9177, 77.6238, MetroLineColor.YELLOW)
    private val sElectronicCity = StopCoord("Electronic City", 12.8452, 77.6602, MetroLineColor.YELLOW)
    private val sKengeri = StopCoord("Kengeri", 12.9080, 77.4765, MetroLineColor.PURPLE)
    private val sWhitefield = StopCoord("Whitefield (Kadugodi)", 12.9951, 77.7598, MetroLineColor.PURPLE)

    val allScenarios: List<TripScenario> = listOf(
        // 1. Phoenix Marketcity -> Acharya Institute (Metro Purple->Green + 4-bus chain + Car)
        TripScenario(
            id = "phoenix_acharya",
            badge = "A",
            isOrangeBadge = true,
            originName = "Phoenix Marketcity",
            originPoint = GeoPoint(12.9970, 77.6970),
            destName = "Acharya College, Soladevanahalli",
            destPoint = GeoPoint(13.0846, 77.4847),
            isNamedPlace = true,
            coordsLabel = "13.0846° N, 77.4847° E",
            aerialDistanceLabel = "25.0 km",
            tag = "25.0 km",
            defaultMode = TransitMode.METRO,
            metroPlan = MetroScenarioPlan(
                boardStation = sSingayyanapalya,
                boardWalkMeters = 466,
                boardWalkMins = 6,
                exitStation = sMadavara,
                exitRoadLabel = "Madavara Metro ➔ Acharya College",
                exitDistLabel = "3.3 km",
                exitMins = 10,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sSingayyanapalya, sKrPura, sBaiyappanahalli, sIndiranagar, sMgRoad, sCubbonPark, sMajestic)),
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sMajestic, sYeshwanthpur, sPeenya, sJalahalli, sNagasandra, sMadavara))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("KBS-12HK", listOf("500-D", "500-CA"), 18, "Singaianapalya Phoenix", "Majestic KBS"),
                        BusScenarioLeg("253-J", listOf("253-K"), 16, "Majestic KBS", "Acharya Institute")
                    ),
                    via = "Via Majestic KBS", stopsSummary = "34 stops", freq = "Every ~9m", hub = "Majestic KBS",
                    boardWalkMeters = 236, boardWalkMins = 3, exitWalkMeters = 134, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Singaianapalya Phoenix", 12.9968, 77.6935),
                        StopCoord("KR Pura Railway Stn", 13.0005, 77.6762),
                        StopCoord("Tin Factory", 12.9960, 77.6611),
                        StopCoord("Indiranagar 100ft", 12.9784, 77.6408),
                        StopCoord("MG Road", 12.9756, 77.6066),
                        StopCoord("Majestic KBS (Transfer 253-J)", 12.9767, 77.5713),
                        StopCoord("Yeshwanthpur TTMC", 13.0238, 77.5504),
                        StopCoord("Jalahalli Cross", 13.0394, 77.5197),
                        StopCoord("Thammenahalli Cross", 13.0792, 77.4930),
                        StopCoord("Acharya Institute", 13.0846, 77.4847)
                    )
                ),
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("500-D", listOf("500-K", "500-CA"), 12, "Singaianapalya Phoenix", "Hebbal"),
                        BusScenarioLeg("276", listOf("276-E"), 10, "Hebbal", "BEL Circle"),
                        BusScenarioLeg("250-P", emptyList(), 9, "BEL Circle", "Jalahalli Cross"),
                        BusScenarioLeg("253-J", listOf("253-A"), 8, "Jalahalli Cross", "Acharya Institute")
                    ),
                    via = "Via Hebbal › BEL › Jalahalli", stopsSummary = "39 stops", freq = "4-Bus Chain", hub = "Hebbal • BEL • Jalahalli",
                    boardWalkMeters = 236, boardWalkMins = 3, exitWalkMeters = 134, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Singaianapalya Phoenix (500-D)", 12.9968, 77.6935),
                        StopCoord("KR Pura", 13.0005, 77.6762),
                        StopCoord("Hebbal Flyover (Transfer 276)", 13.0358, 77.5970),
                        StopCoord("BEL Circle (Transfer 250-P)", 13.0470, 77.5511),
                        StopCoord("Jalahalli Cross (Transfer 253-J)", 13.0394, 77.5197),
                        StopCoord("Peenya 2nd Stage", 13.0580, 77.5030),
                        StopCoord("Thammenahalli Cross", 13.0792, 77.4930),
                        StopCoord("Acharya Institute", 13.0846, 77.4847)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9970, 77.6970), GeoPoint(13.0120, 77.6510), GeoPoint(13.0358, 77.5970), GeoPoint(13.0450, 77.5200), GeoPoint(13.0846, 77.4847)),
                roadDistLabel = "~32.0 km", aerialDistLabel = "25.0 km", etaLabel = "58m", viaLabel = "ORR & Tumkur Rd"
            )
        ),

        // 2. Indiranagar -> Electronic City (Metro Purple -> Green -> Yellow + Bus + Car)
        TripScenario(
            id = "indiranagar_ecity",
            badge = "E",
            isOrangeBadge = false,
            originName = "Indiranagar 100 Ft Rd",
            originPoint = GeoPoint(12.9784, 77.6408),
            destName = "Electronic City Phase 1",
            destPoint = GeoPoint(12.8452, 77.6602),
            isNamedPlace = true,
            coordsLabel = "12.8452° N, 77.6602° E",
            aerialDistanceLabel = "15.1 km",
            tag = "15.1 km",
            defaultMode = TransitMode.METRO,
            metroPlan = MetroScenarioPlan(
                boardStation = sIndiranagar, boardWalkMeters = 280, boardWalkMins = 4,
                exitStation = sElectronicCity, exitRoadLabel = "Electronic City Metro ➔ Gate", exitDistLabel = "450 m", exitMins = 6,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sIndiranagar, sMgRoad, sMajestic)),
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sMajestic, sChickpet, sNationalCollege, sJayanagar, sRvRoad)),
                    MetroScenarioLeg("Yellow", MetroLineColor.YELLOW, listOf(sRvRoad, sSilkBoard, sElectronicCity))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("500-D", listOf("500-CA", "500-A"), 11, "Indiranagar BDA", "Silk Board"),
                        BusScenarioLeg("356-CW", listOf("356-C", "600-F"), 9, "Silk Board", "Electronic City")
                    ),
                    via = "Via Central Silk Board", stopsSummary = "20 stops", freq = "Every ~5m", hub = "Silk Board",
                    boardWalkMeters = 190, boardWalkMins = 2, exitWalkMeters = 220, exitWalkMins = 3,
                    stops = listOf(
                        StopCoord("Indiranagar BDA", 12.9784, 77.6408),
                        StopCoord("Domlur Flyover", 12.9610, 77.6387),
                        StopCoord("Koramangala Water Tank", 12.9345, 77.6265),
                        StopCoord("Central Silk Board (Transfer 356-CW)", 12.9177, 77.6238),
                        StopCoord("Hosa Road", 12.8716, 77.6512),
                        StopCoord("Electronic City Ph 1", 12.8452, 77.6602)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9784, 77.6408), GeoPoint(12.9500, 77.6380), GeoPoint(12.9177, 77.6238), GeoPoint(12.8716, 77.6512), GeoPoint(12.8452, 77.6602)),
                roadDistLabel = "~18.4 km", aerialDistLabel = "15.1 km", etaLabel = "42m", viaLabel = "Inner Ring Rd & Hosur Rd"
            )
        ),

        // 3. Whitefield ITPL -> Kempegowda Int'l Airport (KIA Bus + Cab, No Metro)
        TripScenario(
            id = "itpl_kia",
            badge = "✈",
            isOrangeBadge = true,
            originName = "ITPL Whitefield",
            originPoint = GeoPoint(12.9850, 77.7365),
            destName = "Kempegowda Int’l Airport (KIA)",
            destPoint = GeoPoint(13.1986, 77.7066),
            isNamedPlace = true,
            coordsLabel = "13.1986° N, 77.7066° E",
            aerialDistanceLabel = "24.2 km",
            tag = "Yesterday",
            defaultMode = TransitMode.BUS,
            metroPlan = null,
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("KIA-15", listOf("KIA-15A"), 14, "ITPL Main Gate", "KIA Terminal 1")),
                    via = "Direct Vayu Vajra AC", stopsSummary = "14 stops", freq = "Every ~20m", hub = "Direct",
                    boardWalkMeters = 140, boardWalkMins = 2, exitWalkMeters = 80, exitWalkMins = 1,
                    stops = listOf(
                        StopCoord("ITPL Main Gate", 12.9850, 77.7365),
                        StopCoord("Hoodi Circle", 12.9918, 77.7160),
                        StopCoord("KR Pura", 13.0002, 77.6775),
                        StopCoord("Budigere Cross", 13.0680, 77.7120),
                        StopCoord("Bagalur", 13.1360, 77.6940),
                        StopCoord("KIA Terminal 1", 13.1986, 77.7066)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9850, 77.7365), GeoPoint(13.0350, 77.7210), GeoPoint(13.1200, 77.6950), GeoPoint(13.1986, 77.7066)),
                roadDistLabel = "~33.5 km", aerialDistLabel = "24.2 km", etaLabel = "49m", viaLabel = "Budigere & Airport Trumpet Rd"
            )
        ),

        // 4. Koramangala 4th Block -> Yeshwanthpur
        TripScenario(
            id = "koramangala_yeshwanthpur",
            badge = "Y",
            isOrangeBadge = false,
            originName = "Koramangala 4th Block",
            originPoint = GeoPoint(12.9352, 77.6245),
            destName = "Yeshwanthpur Railway Station",
            destPoint = GeoPoint(13.0238, 77.5504),
            isNamedPlace = true,
            coordsLabel = "13.0238° N, 77.5504° E",
            aerialDistanceLabel = "12.8 km",
            tag = "12.8 km",
            defaultMode = TransitMode.BUS,
            metroPlan = MetroScenarioPlan(
                boardStation = sIndiranagar, boardWalkMeters = 2800, boardWalkMins = 12,
                exitStation = sYeshwanthpur, exitRoadLabel = "Yeshwanthpur Metro ➔ Gate", exitDistLabel = "120 m", exitMins = 2,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sIndiranagar, sMgRoad, sMajestic)),
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sMajestic, sYeshwanthpur))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("171-C", listOf("171-F", "G-3"), 14, "Sony World", "Majestic KBS"),
                        BusScenarioLeg("252-E", listOf("250", "258"), 11, "Majestic KBS", "Yeshwanthpur")
                    ),
                    via = "Via Majestic KBS", stopsSummary = "25 stops", freq = "Every ~7m", hub = "Majestic KBS",
                    boardWalkMeters = 210, boardWalkMins = 3, exitWalkMeters = 150, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Sony World Koramangala", 12.9362, 77.6269),
                        StopCoord("Richmond Circle", 12.9660, 77.5960),
                        StopCoord("Majestic KBS (Transfer 252-E)", 12.9767, 77.5713),
                        StopCoord("Malleswaram Circle", 13.0031, 77.5643),
                        StopCoord("Yeshwanthpur Stn", 13.0238, 77.5504)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9352, 77.6245), GeoPoint(12.9660, 77.5960), GeoPoint(12.9950, 77.5700), GeoPoint(13.0238, 77.5504)),
                roadDistLabel = "~16.2 km", aerialDistLabel = "12.8 km", etaLabel = "39m", viaLabel = "Race Course Rd & Sankey Rd"
            )
        ),

        // 5. MG Road -> Silk Institute (Purple -> Green)
        TripScenario(
            id = "mgroad_silk",
            badge = "S",
            isOrangeBadge = false,
            originName = "MG Road Metro Plaza",
            originPoint = GeoPoint(12.9756, 77.6066),
            destName = "Silk Institute, Kanakapura Rd",
            destPoint = GeoPoint(12.8617, 77.5300),
            isNamedPlace = true,
            coordsLabel = "12.8617° N, 77.5300° E",
            aerialDistanceLabel = "15.2 km",
            tag = "15.2 km",
            defaultMode = TransitMode.METRO,
            metroPlan = MetroScenarioPlan(
                boardStation = sMgRoad, boardWalkMeters = 120, boardWalkMins = 2,
                exitStation = sSilkInstitute, exitRoadLabel = "Silk Institute Exit", exitDistLabel = "180 m", exitMins = 2,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sMgRoad, sCubbonPark, sMajestic)),
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sMajestic, sChickpet, sNationalCollege, sJayanagar, sRvRoad, sBanashankari, sSilkInstitute))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("215-H", listOf("215-B"), 26, "Brigade Rd", "Silk Institute")),
                    via = "Via Banashankari", stopsSummary = "26 stops", freq = "Every ~15m", hub = "Direct",
                    boardWalkMeters = 250, boardWalkMins = 3, exitWalkMeters = 100, exitWalkMins = 1,
                    stops = listOf(
                        StopCoord("Brigade Road", 12.9740, 77.6075),
                        StopCoord("Lalbagh West", 12.9490, 77.5790),
                        StopCoord("Banashankari TTMC", 12.9154, 77.5736),
                        StopCoord("Konanakunte Cross", 12.8890, 77.5580),
                        StopCoord("Silk Institute", 12.8617, 77.5300)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9756, 77.6066), GeoPoint(12.9490, 77.5790), GeoPoint(12.9154, 77.5736), GeoPoint(12.8617, 77.5300)),
                roadDistLabel = "~18.9 km", aerialDistLabel = "15.2 km", etaLabel = "44m", viaLabel = "Kanakapura Rd"
            )
        ),

        // 6. Hebbal -> Marathahalli (ORR 500-D Direct Bus vs Car)
        TripScenario(
            id = "hebbal_marathahalli",
            badge = "5",
            isOrangeBadge = true,
            originName = "Hebbal Flyover",
            originPoint = GeoPoint(13.0358, 77.5970),
            destName = "Marathahalli Bridge",
            destPoint = GeoPoint(12.9592, 77.7010),
            isNamedPlace = true,
            coordsLabel = "12.9592° N, 77.7010° E",
            aerialDistanceLabel = "14.1 km",
            tag = "14.1 km",
            defaultMode = TransitMode.BUS,
            metroPlan = null,
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("500-D", listOf("500-CA", "500-K", "V-500D"), 19, "Hebbal Flyover", "Marathahalli Bridge")),
                    via = "Outer Ring Road", stopsSummary = "19 stops", freq = "Every ~4m", hub = "Direct ORR",
                    boardWalkMeters = 110, boardWalkMins = 2, exitWalkMeters = 90, exitWalkMins = 1,
                    stops = listOf(
                        StopCoord("Hebbal Flyover", 13.0358, 77.5970),
                        StopCoord("Nagawara Junction", 13.0440, 77.6210),
                        StopCoord("Kalyan Nagar", 13.0220, 77.6480),
                        StopCoord("KR Pura Tin Factory", 12.9960, 77.6611),
                        StopCoord("Doddanekundi", 12.9710, 77.6940),
                        StopCoord("Marathahalli Bridge", 12.9592, 77.7010)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(13.0358, 77.5970), GeoPoint(13.0440, 77.6210), GeoPoint(12.9960, 77.6611), GeoPoint(12.9592, 77.7010)),
                roadDistLabel = "~17.6 km", aerialDistLabel = "14.1 km", etaLabel = "38m", viaLabel = "Outer Ring Road"
            )
        ),

        // 7. Majestic -> Whitefield Kadugodi (0-transfer Purple Line)
        TripScenario(
            id = "majestic_whitefield",
            badge = "W",
            isOrangeBadge = false,
            originName = "Majestic Bus / Metro Hub",
            originPoint = GeoPoint(12.9756, 77.5728),
            destName = "Whitefield (Kadugodi)",
            destPoint = GeoPoint(12.9951, 77.7598),
            isNamedPlace = true,
            coordsLabel = "12.9951° N, 77.7598° E",
            aerialDistanceLabel = "20.4 km",
            tag = "20.4 km",
            defaultMode = TransitMode.METRO,
            metroPlan = MetroScenarioPlan(
                boardStation = sMajestic, boardWalkMeters = 80, boardWalkMins = 1,
                exitStation = sWhitefield, exitRoadLabel = "Kadugodi Metro Exit", exitDistLabel = "150 m", exitMins = 2,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sMajestic, sCubbonPark, sMgRoad, sIndiranagar, sBaiyappanahalli, sKrPura, sSingayyanapalya, sWhitefield))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("335-E", listOf("V-335E", "333-P"), 28, "Majestic KBS", "Kadugodi")),
                    via = "Via HAL Airport Rd", stopsSummary = "28 stops", freq = "Every ~8m", hub = "Direct",
                    boardWalkMeters = 100, boardWalkMins = 2, exitWalkMeters = 120, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Majestic KBS", 12.9767, 77.5713),
                        StopCoord("Manipal Hospital", 12.9590, 77.6480),
                        StopCoord("Marathahalli", 12.9592, 77.7010),
                        StopCoord("Hope Farm", 12.9840, 77.7520),
                        StopCoord("Kadugodi", 12.9951, 77.7598)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9756, 77.5728), GeoPoint(12.9756, 77.6066), GeoPoint(12.9910, 77.6850), GeoPoint(12.9951, 77.7598)),
                roadDistLabel = "~24.8 km", aerialDistLabel = "20.4 km", etaLabel = "55m", viaLabel = "Old Madras Rd"
            )
        ),

        // 8. Banashankari -> Manyata Tech Park (Green->Purple + 3-Bus Chain)
        TripScenario(
            id = "bsk_manyata",
            badge = "M",
            isOrangeBadge = false,
            originName = "Banashankari TTMC",
            originPoint = GeoPoint(12.9154, 77.5736),
            destName = "Manyata Tech Park",
            destPoint = GeoPoint(13.0458, 77.6203),
            isNamedPlace = true,
            coordsLabel = "13.0458° N, 77.6203° E",
            aerialDistanceLabel = "15.4 km",
            tag = "15.4 km",
            defaultMode = TransitMode.BUS,
            metroPlan = MetroScenarioPlan(
                boardStation = sBanashankari, boardWalkMeters = 150, boardWalkMins = 2,
                exitStation = sBaiyappanahalli, exitRoadLabel = "Baiyappanahalli ➔ Manyata", exitDistLabel = "6.8 km", exitMins = 16,
                legs = listOf(
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sBanashankari, sRvRoad, sJayanagar, sNationalCollege, sChickpet, sMajestic)),
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sMajestic, sCubbonPark, sMgRoad, sIndiranagar, sBaiyappanahalli))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("210-N", listOf("210-A"), 11, "Banashankari", "KR Market"),
                        BusScenarioLeg("290-E", listOf("291"), 12, "KR Market", "Nagawara"),
                        BusScenarioLeg("500-D", listOf("500-K"), 3, "Nagawara", "Manyata Gate")
                    ),
                    via = "Via KR Market › Nagawara", stopsSummary = "26 stops", freq = "3-Bus Chain", hub = "KR Mkt • Nagawara",
                    boardWalkMeters = 90, boardWalkMins = 1, exitWalkMeters = 180, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Banashankari TTMC (210-N)", 12.9154, 77.5736),
                        StopCoord("KR Market (Transfer 290-E)", 12.9635, 77.5772),
                        StopCoord("Shivajinagar", 12.9857, 77.6057),
                        StopCoord("Nagawara (Transfer 500-D)", 13.0440, 77.6210),
                        StopCoord("Manyata Tech Park", 13.0458, 77.6203)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9154, 77.5736), GeoPoint(12.9635, 77.5772), GeoPoint(13.0050, 77.5950), GeoPoint(13.0458, 77.6203)),
                roadDistLabel = "~20.1 km", aerialDistLabel = "15.4 km", etaLabel = "47m", viaLabel = "Jayamahal Rd & Nagawara"
            )
        ),

        // 9. HSR Layout -> Unnamed Dropped Pin (Raw Coordinates Demo)
        TripScenario(
            id = "hsr_pin",
            badge = "P",
            isOrangeBadge = true,
            originName = "HSR Layout Sector 2",
            originPoint = GeoPoint(12.9121, 77.6446),
            destName = "12.9716° N, 77.5946° E",
            destPoint = GeoPoint(12.9716, 77.5946),
            isNamedPlace = false,
            coordsLabel = "12.9716° N, 77.5946° E",
            aerialDistanceLabel = "8.5 km",
            tag = "Pin • 8.5 km",
            defaultMode = TransitMode.CAR,
            metroPlan = null,
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("342-F", listOf("342-A", "342-B"), 14, "HSR BDA", "Richmond Circle")),
                    via = "Via Koramangala", stopsSummary = "14 stops", freq = "Every ~10m", hub = "Direct",
                    boardWalkMeters = 310, boardWalkMins = 4, exitWalkMeters = 260, exitWalkMins = 3,
                    stops = listOf(
                        StopCoord("HSR BDA Complex", 12.9121, 77.6446),
                        StopCoord("Koramangala 1st Block", 12.9279, 77.6320),
                        StopCoord("Shantinagar", 12.9540, 77.5980),
                        StopCoord("Richmond Circle", 12.9660, 77.5960),
                        StopCoord("Dropped Pin (12.9716, 77.5946)", 12.9716, 77.5946)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9121, 77.6446), GeoPoint(12.9350, 77.6240), GeoPoint(12.9540, 77.5980), GeoPoint(12.9716, 77.5946)),
                roadDistLabel = "~10.4 km", aerialDistLabel = "8.5 km", etaLabel = "28m", viaLabel = "Sarjapur Rd & Richmond Rd"
            )
        ),

        // 10. Jayanagar 4th Block -> Cubbon Park (Short Metro Hop)
        TripScenario(
            id = "jayanagar_cubbon",
            badge = "C",
            isOrangeBadge = false,
            originName = "Jayanagar 4th Block",
            originPoint = GeoPoint(12.9295, 77.5801),
            destName = "Cubbon Park",
            destPoint = GeoPoint(12.9809, 77.5975),
            isNamedPlace = true,
            coordsLabel = "12.9809° N, 77.5975° E",
            aerialDistanceLabel = "6.0 km",
            tag = "6.0 km",
            defaultMode = TransitMode.METRO,
            metroPlan = MetroScenarioPlan(
                boardStation = sJayanagar, boardWalkMeters = 180, boardWalkMins = 2,
                exitStation = sCubbonPark, exitRoadLabel = "Cubbon Park Gate", exitDistLabel = "90 m", exitMins = 1,
                legs = listOf(
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sJayanagar, sNationalCollege, sChickpet, sMajestic)),
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sMajestic, sCubbonPark))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(BusScenarioLeg("25-A", listOf("2", "215"), 9, "Jayanagar 4th Blk", "Chinnaswamy")),
                    via = "Via Lalbagh", stopsSummary = "9 stops", freq = "Every ~6m", hub = "Direct",
                    boardWalkMeters = 140, boardWalkMins = 2, exitWalkMeters = 190, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Jayanagar 4th Block", 12.9295, 77.5801),
                        StopCoord("Lalbagh Main Gate", 12.9507, 77.5848),
                        StopCoord("Corporation Circle", 12.9680, 77.5860),
                        StopCoord("Chinnaswamy / Cubbon", 12.9809, 77.5975)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9295, 77.5801), GeoPoint(12.9507, 77.5848), GeoPoint(12.9680, 77.5860), GeoPoint(12.9809, 77.5975)),
                roadDistLabel = "~7.2 km", aerialDistLabel = "6.0 km", etaLabel = "21m", viaLabel = "Lalbagh Rd & Kasturba Rd"
            )
        ),

        // 11. Kengeri -> Yelahanka (Cross-city 3-bus vs Direct Road vs Metro+Bus)
        TripScenario(
            id = "kengeri_yelahanka",
            badge = "K",
            isOrangeBadge = true,
            originName = "Kengeri Satellite Town",
            originPoint = GeoPoint(12.9080, 77.4765),
            destName = "Yelahanka New Town",
            destPoint = GeoPoint(13.1007, 77.5963),
            isNamedPlace = true,
            coordsLabel = "13.1007° N, 77.5963° E",
            aerialDistanceLabel = "25.1 km",
            tag = "2d ago",
            defaultMode = TransitMode.CAR,
            metroPlan = MetroScenarioPlan(
                boardStation = sKengeri, boardWalkMeters = 320, boardWalkMins = 4,
                exitStation = sYeshwanthpur, exitRoadLabel = "Yeshwanthpur ➔ Yelahanka", exitDistLabel = "10.2 km", exitMins = 22,
                legs = listOf(
                    MetroScenarioLeg("Purple", MetroLineColor.PURPLE, listOf(sKengeri, sMajestic)),
                    MetroScenarioLeg("Green", MetroLineColor.GREEN, listOf(sMajestic, sYeshwanthpur))
                )
            ),
            busOptions = listOf(
                BusScenarioOption(
                    legs = listOf(
                        BusScenarioLeg("225-C", listOf("226-N"), 14, "Kengeri", "Majestic KBS"),
                        BusScenarioLeg("285-M", listOf("401-K"), 16, "Majestic KBS", "Yelahanka"),
                        BusScenarioLeg("284", emptyList(), 5, "Yelahanka Old", "New Town")
                    ),
                    via = "Via Majestic › Hebbal", stopsSummary = "35 stops", freq = "3-Bus Chain", hub = "Majestic • Yelahanka",
                    boardWalkMeters = 180, boardWalkMins = 2, exitWalkMeters = 140, exitWalkMins = 2,
                    stops = listOf(
                        StopCoord("Kengeri TTMC (225-C)", 12.9080, 77.4765),
                        StopCoord("Nayandahalli", 12.9420, 77.5230),
                        StopCoord("Majestic KBS (Transfer 285-M)", 12.9767, 77.5713),
                        StopCoord("Hebbal Flyover", 13.0358, 77.5970),
                        StopCoord("Yelahanka Old (Transfer 284)", 13.0950, 77.5960),
                        StopCoord("Yelahanka New Town", 13.1007, 77.5963)
                    )
                )
            ),
            carPlan = CarScenarioPlan(
                roadPoints = listOf(GeoPoint(12.9080, 77.4765), GeoPoint(12.9800, 77.5150), GeoPoint(13.0358, 77.5970), GeoPoint(13.1007, 77.5963)),
                roadDistLabel = "~31.4 km", aerialDistLabel = "25.1 km", etaLabel = "54m", viaLabel = "NICE Rd / ORR & Bellary Rd"
            )
        )
    )
}
