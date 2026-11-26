package com.wakeme.uipreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WakeMeUiLabApp()
            }
        }
    }
}

private enum class ModalKind { NONE, SAVED, HISTORY, SCENARIOS, SETTINGS, REMINDER }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WakeMeUiLabApp() {
    var screen by remember { mutableStateOf(PreviewScreen.LANDING) }
    var scenario by remember { mutableStateOf(ScenarioFixtures.allScenarios.first()) }
    var mode by remember { mutableStateOf(scenario.defaultMode) }
    var themePref by remember { mutableStateOf(ThemePreference.SYSTEM) }
    var scheme by remember { mutableStateOf(ColorSchemePreset.SIGNAL) }
    val motion = MotionPreset.ELASTIC
    val isDark = when (themePref) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    var selectedBusIdx by remember { mutableIntStateOf(0) }
    var activeStageIdx by remember { mutableIntStateOf(0) }
    var activeTripProgress by remember { mutableFloatStateOf(0.10f) }
    var autoSimRunning by remember { mutableStateOf(true) }

    var transitLeadStops by remember { mutableIntStateOf(1) }
    var roadLeadMeters by remember { mutableIntStateOf(1000) }
    var ringStyle by remember { mutableStateOf(RingStyle.RING_VIBRATE) }

    var modalKind by remember { mutableStateOf(ModalKind.NONE) }
    var focusedLegStage by remember { mutableStateOf<Int?>(null) }
    var nearestFocus by remember { mutableStateOf<NearestStationFocus?>(null) }

    val savedScenarios = remember {
        mutableStateListOf(
            ScenarioFixtures.allScenarios[0],
            ScenarioFixtures.allScenarios[1],
            ScenarioFixtures.allScenarios[8]
        )
    }
    val historyScenarios = remember {
        ScenarioFixtures.allScenarios.slice(listOf(0, 2, 4, 6, 9, 10))
    }

    val p = paletteFor(scheme, isDark)
    val animSpec = motionSpecFor(motion)
    val offsetSpec = motionOffsetSpecFor(motion)
    val durMs = motionDurationMs(motion)

    // Automatic GPS-like progress simulation during Active Trip
    LaunchedEffect(screen, activeStageIdx, autoSimRunning, scenario.id, mode) {
        if (screen == PreviewScreen.ACTIVE && autoSimRunning) {
            while (true) {
                delay(180L)
                activeTripProgress = if (activeTripProgress >= 0.99f) 0.04f else (activeTripProgress + 0.015f).coerceAtMost(1f)
            }
        }
    }

    BackHandler(enabled = modalKind != ModalKind.NONE || focusedLegStage != null || nearestFocus != null || screen != PreviewScreen.LANDING) {
        when {
            modalKind != ModalKind.NONE -> modalKind = ModalKind.NONE
            nearestFocus != null -> nearestFocus = null
            focusedLegStage != null -> focusedLegStage = null
            screen == PreviewScreen.ACTIVE -> screen = PreviewScreen.SETUP
            screen == PreviewScreen.SETUP -> screen = PreviewScreen.LANDING
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.mapBg)
    ) {
        // 1. Real OpenStreetMap (OSMDroid) Canvas
        OsmMapCanvas(
            palette = p,
            isDark = isDark,
            scenario = scenario,
            screen = screen,
            mode = mode,
            selectedBusIdx = selectedBusIdx,
            focusedLegStage = focusedLegStage,
            activeStageIdx = activeStageIdx,
            activeTripProgress = activeTripProgress,
            nearestFocus = nearestFocus
        )

        // 2. Top Safe-Area Controls (single AnimatedContent slot prevents Page 1 -> Page 2 jump)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            AnimatedContent(
                targetState = screen == PreviewScreen.LANDING,
                transitionSpec = {
                    (fadeIn(tween(durMs)) + slideInVertically(offsetSpec) { -it / 6 }) togetherWith
                        (fadeOut(tween(durMs / 2)) + slideOutVertically(offsetSpec) { -it / 6 })
                },
                label = "topBannerSwap"
            ) { isLanding ->
                if (isLanding) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { modalKind = ModalKind.SCENARIOS },
                            shape = RoundedCornerShape(999.dp),
                            color = p.surface.copy(alpha = 0.96f),
                            border = BorderStroke(1.dp, p.hairline),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = p.blue, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (scenario.isNamedPlace) scenario.destName else scenario.coordsLabel,
                                    color = p.muted,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    onClick = { modalKind = ModalKind.SETTINGS },
                                    shape = CircleShape,
                                    color = p.bg
                                ) {
                                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = p.ink, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Page 1 Quick Pills: Saved Places & History
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickPillChip("Saved", "${savedScenarios.size}", p) { modalKind = ModalKind.SAVED }
                            QuickPillChip("History", "${historyScenarios.size}", p) { modalKind = ModalKind.HISTORY }
                        }
                    }
                } else {
                    val (cueArrow, cueMain, cueSub) = directionCueFor(
                        screen = screen,
                        scenario = scenario,
                        mode = mode,
                        activeStageIdx = activeStageIdx,
                        selectedBusIdx = selectedBusIdx,
                        focusedLegStage = focusedLegStage,
                        transitLeadStops = transitLeadStops,
                        roadLeadMeters = roadLeadMeters
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = p.cueBg,
                        border = BorderStroke(1.dp, p.hairline),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.orange),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cueArrow, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cueMain, color = p.cueInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cueSub, color = p.cueInk.copy(alpha = 0.78f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (focusedLegStage != null) {
                                Surface(
                                    onClick = { focusedLegStage = null },
                                    shape = RoundedCornerShape(999.dp),
                                    color = p.orange
                                ) {
                                    Text(
                                        "Show All",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right-edge Circular Metro & Bus Nearest-Station Buttons (Page 1, matching WakeMe app)
        if (screen == PreviewScreen.LANDING) {
            val metroPlan = scenario.metroPlan
            val busOpt = scenario.busOptions.first()
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (nearestFocus?.isMetro == true) p.purple else p.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.5.dp, if (nearestFocus?.isMetro == true) p.purple else p.hairline),
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .combinedClickable(
                                onClick = {
                                    nearestFocus = if (metroPlan != null) {
                                        NearestStationFocus(
                                            isMetro = true,
                                            stationName = "${metroPlan.boardStation.name} Metro",
                                            distanceLabel = "${metroPlan.boardWalkMeters}m • ${metroPlan.boardWalkMins}m walk",
                                            subtitle = "${metroPlan.legs.first().lineName} Line • Hold to navigate",
                                            point = metroPlan.boardStation.geoPoint,
                                            zoomToIt = false
                                        )
                                    } else {
                                        NearestStationFocus(
                                            isMetro = true,
                                            stationName = "No Metro Nearby",
                                            distanceLabel = "> 3 km away",
                                            subtitle = "Try the Bus stop button below",
                                            point = scenario.originPoint,
                                            zoomToIt = false
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (metroPlan != null) {
                                        nearestFocus = NearestStationFocus(
                                            isMetro = true,
                                            stationName = "${metroPlan.boardStation.name} Metro",
                                            distanceLabel = "${metroPlan.boardWalkMeters}m • ${metroPlan.boardWalkMins}m walk",
                                            subtitle = "Navigating to nearest Metro station",
                                            point = metroPlan.boardStation.geoPoint,
                                            zoomToIt = true
                                        )
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subway,
                            contentDescription = "Nearest Metro",
                            tint = if (nearestFocus?.isMetro == true) Color.White else p.purple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (nearestFocus?.isMetro == false) p.blue else p.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.5.dp, if (nearestFocus?.isMetro == false) p.blue else p.hairline),
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .combinedClickable(
                                onClick = {
                                    val stop = busOpt.stops.first()
                                    nearestFocus = NearestStationFocus(
                                        isMetro = false,
                                        stationName = stop.name,
                                        distanceLabel = "${busOpt.boardWalkMeters}m • ${busOpt.boardWalkMins}m walk",
                                        subtitle = "${busOpt.leg1} • Hold to navigate",
                                        point = stop.geoPoint,
                                        zoomToIt = false
                                    )
                                },
                                onLongClick = {
                                    val stop = busOpt.stops.first()
                                    nearestFocus = NearestStationFocus(
                                        isMetro = false,
                                        stationName = stop.name,
                                        distanceLabel = "${busOpt.boardWalkMeters}m • ${busOpt.boardWalkMins}m walk",
                                        subtitle = "Navigating to nearest Bus stop",
                                        point = stop.geoPoint,
                                        zoomToIt = true
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = "Nearest Bus",
                            tint = if (nearestFocus?.isMetro == false) p.badgeInk else p.blue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (nearestFocus != null) {
                val nf = nearestFocus!!
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 104.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = p.cueBg,
                    border = BorderStroke(1.dp, p.hairline),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (nf.isMetro) Icons.Default.Subway else Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = if (nf.isMetro) p.purple else p.blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${nf.stationName} • ${nf.distanceLabel}", color = p.cueInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nf.subtitle, color = p.cueInk.copy(alpha = 0.78f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            onClick = { nearestFocus = null },
                            shape = RoundedCornerShape(999.dp),
                            color = p.orange
                        ) {
                            Text("Clear", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // 3. Persistent Bottom Sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = p.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(p.muted.copy(alpha = 0.30f))
                )
                Spacer(Modifier.height(10.dp))

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(durMs)) + slideInVertically(offsetSpec) { it / 6 }) togetherWith
                            fadeOut(tween(durMs / 2))
                    },
                    label = "sheetContent"
                ) { current ->
                    when (current) {
                        PreviewScreen.LANDING -> LandingSheetContent(
                            palette = p,
                            scenario = scenario,
                            motion = motion,
                            onNext = {
                                focusedLegStage = null
                                selectedBusIdx = 0
                                screen = PreviewScreen.SETUP
                            }
                        )
                        PreviewScreen.SETUP -> SetupSheetContent(
                            palette = p,
                            scenario = scenario,
                            mode = mode,
                            motion = motion,
                            selectedBusIdx = selectedBusIdx,
                            focusedLegStage = focusedLegStage,
                            transitLeadStops = transitLeadStops,
                            roadLeadMeters = roadLeadMeters,
                            onSelectMode = {
                                mode = it
                                activeStageIdx = 0
                                focusedLegStage = null
                            },
                            onSelectBus = { selectedBusIdx = it },
                            onTapLeg = { stage ->
                                focusedLegStage = if (focusedLegStage == stage) null else stage
                            },
                            onOpenReminder = { modalKind = ModalKind.REMINDER },
                            onStart = {
                                focusedLegStage = null
                                activeStageIdx = 0
                                activeTripProgress = 0.08f
                                autoSimRunning = true
                                screen = PreviewScreen.ACTIVE
                            }
                        )
                        PreviewScreen.ACTIVE -> ActiveSheetContent(
                            palette = p,
                            scenario = scenario,
                            mode = mode,
                            motion = motion,
                            activeStageIdx = activeStageIdx,
                            activeTripProgress = activeTripProgress,
                            autoSimRunning = autoSimRunning,
                            selectedBusIdx = selectedBusIdx,
                            transitLeadStops = transitLeadStops,
                            roadLeadMeters = roadLeadMeters,
                            ringStyle = ringStyle,
                            onSelectStage = {
                                activeStageIdx = it
                                activeTripProgress = 0.08f
                            },
                            onProgressChange = { activeTripProgress = it },
                            onToggleSim = { autoSimRunning = !autoSimRunning },
                            onEnd = {
                                focusedLegStage = null
                                screen = PreviewScreen.LANDING
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // 4. Overlay Modal Sheets (Saved, History, Scenarios, Settings, Reminder)
        AnimatedVisibility(
            visible = modalKind != ModalKind.NONE,
            enter = fadeIn(tween(durMs)),
            exit = fadeOut(tween(durMs / 2))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clickable { modalKind = ModalKind.NONE }
            )
        }
        AnimatedVisibility(
            visible = modalKind != ModalKind.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(offsetSpec) { it },
            exit = slideOutVertically(offsetSpec) { it }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = p.surface,
                shadowElevation = 20.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(p.muted.copy(alpha = 0.30f))
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (modalKind) {
                                ModalKind.SAVED -> "Saved Places"
                                ModalKind.HISTORY -> "Recent Commutes"
                                ModalKind.SCENARIOS -> "Explore 11 Routes"
                                ModalKind.SETTINGS -> "Appearance & Theme"
                                ModalKind.REMINDER -> "Reminder & Ring Style"
                                ModalKind.NONE -> ""
                            },
                            color = p.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (modalKind == ModalKind.SAVED) {
                                Surface(
                                    onClick = {
                                        val nextCandidate = ScenarioFixtures.allScenarios.firstOrNull { cand ->
                                            savedScenarios.none { it.id == cand.id }
                                        }
                                        if (nextCandidate != null) {
                                            savedScenarios.add(0, nextCandidate)
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = p.blue
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = p.badgeInk, modifier = Modifier.size(14.dp))
                                        Text("Add", color = p.badgeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Surface(
                                onClick = { modalKind = ModalKind.NONE },
                                shape = RoundedCornerShape(999.dp),
                                color = p.bg
                            ) {
                                Text(
                                    "Close",
                                    color = p.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = p.hairline)

                    when (modalKind) {
                        ModalKind.SETTINGS -> {
                            Spacer(Modifier.height(10.dp))
                            Text("Theme Mode", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(p.bg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThemePreference.values().forEach { pref ->
                                    val selected = pref == themePref
                                    Surface(
                                        onClick = { themePref = pref },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) p.surface else Color.Transparent,
                                        shadowElevation = if (selected) 2.dp else 0.dp
                                    ) {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                                            Text(pref.label, color = if (selected) p.ink else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text("Color Scheme", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            ColorSchemePreset.values().forEachIndexed { idx, preset ->
                                val swatch = paletteFor(preset, isDark)
                                val selected = preset == scheme
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { scheme = preset }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Accurate 4-disc swatch: bg, surface, primary (blue), alert (orange)
                                    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                                        listOf(swatch.bg, swatch.surface, swatch.blue, swatch.orange).forEach { col ->
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(col)
                                                    .border(1.dp, p.hairline, CircleShape)
                                            )
                                        }
                                    }
                                    Text(
                                        text = preset.label,
                                        color = p.ink,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = p.blue, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (idx < ColorSchemePreset.values().lastIndex) HorizontalDivider(color = p.hairline)
                            }
                        }

                        ModalKind.REMINDER -> {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (mode == TransitMode.CAR) "Alert Distance Before Destination" else "Alert Stops Before Exit / Transfer",
                                color = p.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(p.bg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (mode == TransitMode.CAR) {
                                    listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km").forEach { (meters, label) ->
                                        val selected = roadLeadMeters == meters
                                        Surface(
                                            onClick = { roadLeadMeters = meters },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) p.surface else Color.Transparent,
                                            shadowElevation = if (selected) 2.dp else 0.dp
                                        ) {
                                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                                                Text(label, color = if (selected) p.ink else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else {
                                    listOf(1 to "1 stop", 2 to "2 stops", 3 to "3 stops").forEach { (stops, label) ->
                                        val selected = transitLeadStops == stops
                                        Surface(
                                            onClick = { transitLeadStops = stops },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) p.surface else Color.Transparent,
                                            shadowElevation = if (selected) 2.dp else 0.dp
                                        ) {
                                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                                                Text(label, color = if (selected) p.ink else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text("How It Rings", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            RingStyle.values().forEachIndexed { idx, style ->
                                val selected = style == ringStyle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { ringStyle = style }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = if (selected) p.orange else p.muted, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = style.label,
                                        color = p.ink,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = p.blue, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (idx < RingStyle.values().lastIndex) HorizontalDivider(color = p.hairline)
                            }
                        }

                        ModalKind.SAVED, ModalKind.HISTORY, ModalKind.SCENARIOS -> {
                            val list = when (modalKind) {
                                ModalKind.SAVED -> savedScenarios
                                ModalKind.HISTORY -> historyScenarios
                                else -> ScenarioFixtures.allScenarios
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                list.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scenario = item
                                                mode = item.defaultMode
                                                selectedBusIdx = 0
                                                activeStageIdx = 0
                                                focusedLegStage = null
                                                modalKind = ModalKind.NONE
                                                screen = PreviewScreen.SETUP
                                            }
                                            .padding(vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (item.isOrangeBadge) p.orange.copy(alpha = 0.14f) else p.bg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                item.badge,
                                                color = if (item.isOrangeBadge) p.orange else p.blue,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.destName, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${item.originName} → ${item.defaultMode.label}", color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(item.tag, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (idx < list.lastIndex) HorizontalDivider(color = p.hairline)
                                }
                            }
                        }

                        ModalKind.NONE -> Unit
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickPillChip(title: String, count: String, palette: WakeMePalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = palette.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, palette.hairline),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.bg)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(count, color = palette.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModeQuickPill(title: String, icon: ImageVector, selected: Boolean, palette: WakeMePalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) palette.blue else palette.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, if (selected) palette.blue else palette.hairline),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) palette.badgeInk else palette.ink, modifier = Modifier.size(14.dp))
            Text(title, color = if (selected) palette.badgeInk else palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LandingSheetContent(
    palette: WakeMePalette,
    scenario: TripScenario,
    motion: MotionPreset,
    onNext: () -> Unit
) {
    val primaryLabel = if (scenario.isNamedPlace) scenario.destName else scenario.coordsLabel
    val secondaryLabel = if (scenario.isNamedPlace) {
        "${scenario.aerialDistanceLabel} from ${scenario.originName}"
    } else {
        "${scenario.aerialDistanceLabel} from ${scenario.originName} • Dropped Pin"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryLabel,
                color = palette.ink,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = secondaryLabel,
                color = palette.muted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressScaleFor(motion) else 1f,
            animationSpec = tween(motionDurationMs(motion).coerceAtMost(180)),
            label = "nextBtnScale"
        )
        Surface(
            onClick = onNext,
            interactionSource = interactionSource,
            shape = CircleShape,
            color = palette.ctaBg,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next step",
                    tint = palette.ctaInk,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupSheetContent(
    palette: WakeMePalette,
    scenario: TripScenario,
    mode: TransitMode,
    motion: MotionPreset,
    selectedBusIdx: Int,
    focusedLegStage: Int?,
    transitLeadStops: Int,
    roadLeadMeters: Int,
    onSelectMode: (TransitMode) -> Unit,
    onSelectBus: (Int) -> Unit,
    onTapLeg: (Int) -> Unit,
    onOpenReminder: () -> Unit,
    onStart: () -> Unit
) {
    var stopsExpanded by remember(scenario.id, mode, selectedBusIdx) { mutableStateOf(false) }
    val busOpt = scenario.busOptions[selectedBusIdx.coerceIn(0, scenario.busOptions.lastIndex)]
    val metro = scenario.metroPlan
    val title = when (mode) {
        TransitMode.METRO -> if (metro != null) "${scenario.destName} • ${metro.totalStopsCount} Metro Stops" else "${scenario.destName} • No Direct Metro"
        TransitMode.BUS -> "${scenario.destName} • ${busOpt.chainSummary}"
        TransitMode.CAR -> "${scenario.destName} • Direct Road"
    }
    val durMs = motionDurationMs(motion)
    val roadLabel = if (roadLeadMeters >= 1000) "${roadLeadMeters / 1000} km" else "${roadLeadMeters} m"

    Column {
        Text(scenario.originName, color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

        // Segmented Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(palette.bg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TransitMode.values().forEach { item ->
                val selected = item == mode
                Surface(
                    onClick = { onSelectMode(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) palette.surface else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.label,
                            color = if (selected) palette.ink else palette.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // BMTC Route Cards (supports 1 to 4+ bus legs & shared-corridor badges)
        AnimatedVisibility(
            visible = mode == TransitMode.BUS && focusedLegStage == null,
            enter = expandVertically(tween(durMs)) + fadeIn(tween(durMs)),
            exit = shrinkVertically(tween(durMs)) + fadeOut(tween(durMs))
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scenario.busOptions.forEachIndexed { idx, opt ->
                        val sel = idx == selectedBusIdx
                        Surface(
                            onClick = { onSelectBus(idx) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) palette.blue.copy(alpha = 0.12f) else palette.bg,
                            border = BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) palette.blue else palette.hairline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(216.dp)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    opt.legs.forEachIndexed { legIdx, leg ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(palette.blue)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (leg.sharedRoutes.isNotEmpty()) "${leg.primaryRoute} (+${leg.sharedRoutes.size})" else leg.primaryRoute,
                                                color = palette.badgeInk,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                        if (legIdx < opt.legs.lastIndex) {
                                            Text("›", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${opt.legs.size} ${if (opt.legs.size == 1) "bus" else "buses"} • ${opt.stopsSummary}",
                                        color = palette.ink,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(opt.freq, color = palette.muted, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }
                val sharedSummary = busOpt.legs
                    .filter { it.sharedRoutes.isNotEmpty() }
                    .joinToString(" • ") { "${it.primaryRoute} = ${it.sharedRoutes.joinToString("/")}" }
                if (sharedSummary.isNotEmpty()) {
                    Text(
                        text = "Same corridor: $sharedSummary",
                        color = palette.blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        when (mode) {
            TransitMode.METRO -> {
                if (metro == null) {
                    TimelineRow(palette, palette.muted, "No Direct Metro Corridor", "Try City Bus or Direct Road for ${scenario.destName}", "", false)
                } else {
                    val metroStops = metro.allStops
                    val alertStopIdx = (metroStops.size - 1 - transitLeadStops).coerceIn(0, metroStops.lastIndex)
                    InteractiveTimelineRow(
                        palette = palette,
                        dotColor = palette.orange,
                        title = "1. Reach ${metro.boardStation.name} Metro",
                        subtitle = "Tap for map • Switches at 50m",
                        value = "${metro.boardWalkMeters}m • ${metro.boardWalkMins}m",
                        isOrange = true,
                        isFocused = focusedLegStage == 0,
                        onClick = { onTapLeg(0) },
                        onLongClick = { onTapLeg(0) }
                    )
                    HorizontalDivider(color = palette.hairline)
                    InteractiveTimelineRow(
                        palette = palette,
                        dotColor = palette.purple,
                        title = "2. ${metro.summaryLine}",
                        subtitle = if (stopsExpanded) "Tap for map • Alert at ${metroStops[alertStopIdx].name}" else "Tap for map • Hold for stops • Alert $transitLeadStops before",
                        value = "${metro.totalStopsCount} stops",
                        isOrange = false,
                        isFocused = focusedLegStage == 1,
                        trailingIcon = if (stopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        onClick = { onTapLeg(1) },
                        onLongClick = { stopsExpanded = !stopsExpanded }
                    )
                    AnimatedVisibility(
                        visible = stopsExpanded,
                        enter = expandVertically(tween(durMs)) + fadeIn(tween(durMs)),
                        exit = shrinkVertically(tween(durMs)) + fadeOut(tween(durMs))
                    ) {
                        InlineStopList(
                            stops = metroStops,
                            palette = palette,
                            fallbackColor = palette.purple,
                            alertStopIdx = alertStopIdx
                        )
                    }
                    HorizontalDivider(color = palette.hairline)
                    InteractiveTimelineRow(
                        palette = palette,
                        dotColor = palette.orange,
                        title = "3. ${metro.exitRoadLabel}",
                        subtitle = "Tap for final road path • Alerts at 150m",
                        value = "${metro.exitDistLabel} • ${metro.exitMins}m",
                        isOrange = true,
                        isFocused = focusedLegStage == 2,
                        onClick = { onTapLeg(2) },
                        onLongClick = { onTapLeg(2) }
                    )
                }
            }
            TransitMode.BUS -> {
                val busStops = busOpt.stops
                val alertStopIdx = (busStops.size - 1 - transitLeadStops).coerceIn(0, busStops.lastIndex)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "1. Walk to ${busOpt.legs.first().boardStop}",
                    subtitle = "Tap for pole path • Switches at 50m",
                    value = "${busOpt.boardWalkMeters}m • ${busOpt.boardWalkMins}m",
                    isOrange = true,
                    isFocused = focusedLegStage == 0,
                    onClick = { onTapLeg(0) },
                    onLongClick = { onTapLeg(0) }
                )
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.blue,
                    title = "2. ${busOpt.chainSummary}",
                    subtitle = if (stopsExpanded) "Tap for map • Alert at ${busStops[alertStopIdx].name}" else "Tap for map • Hold for stops • ${busOpt.via}",
                    value = busOpt.stopsSummary,
                    isOrange = false,
                    isFocused = focusedLegStage == 1,
                    trailingIcon = if (stopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    onClick = { onTapLeg(1) },
                    onLongClick = { stopsExpanded = !stopsExpanded }
                )
                AnimatedVisibility(
                    visible = stopsExpanded,
                    enter = expandVertically(tween(durMs)) + fadeIn(tween(durMs)),
                    exit = shrinkVertically(tween(durMs)) + fadeOut(tween(durMs))
                ) {
                    InlineStopList(
                        stops = busStops,
                        palette = palette,
                        fallbackColor = palette.blue,
                        alertStopIdx = alertStopIdx
                    )
                }
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "3. Exit to ${scenario.destName}",
                    subtitle = "Tap for final walk • Alerts at 150m",
                    value = "${busOpt.exitWalkMeters}m • ${busOpt.exitWalkMins}m",
                    isOrange = true,
                    isFocused = focusedLegStage == 2,
                    onClick = { onTapLeg(2) },
                    onLongClick = { onTapLeg(2) }
                )
            }
            TransitMode.CAR -> {
                TimelineRow(palette, palette.blue, "Real-Road Route (${scenario.carPlan.aerialDistLabel} aerial)", "Via ${scenario.carPlan.viaLabel}", "${scenario.carPlan.roadDistLabel} • ${scenario.carPlan.etaLabel}", false)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.orange, "Road Alert Threshold", "Gentle reminder fires when $roadLabel remains", roadLabel, true)
            }
        }

        Spacer(Modifier.height(10.dp))
        val leadLabel = if (mode == TransitMode.CAR) roadLabel else "$transitLeadStops ${if (transitLeadStops == 1) "stop" else "stops"}"
        val ctaText = when (mode) {
            TransitMode.METRO -> "Start Metro ($leadLabel before)"
            TransitMode.BUS -> "Start Bus ($leadLabel before)"
            TransitMode.CAR -> "Start Road Alarm ($leadLabel)"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenReminder,
                shape = RoundedCornerShape(14.dp),
                color = palette.bg,
                border = BorderStroke(1.dp, palette.hairline)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Alert settings", tint = palette.orange, modifier = Modifier.size(17.dp))
                    Text(leadLabel, color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                TactileCtaButton(ctaText, palette.ctaBg, palette.ctaInk, motion, onStart)
            }
        }
    }
}

@Composable
private fun InlineStopList(
    stops: List<StopCoord>,
    palette: WakeMePalette,
    fallbackColor: Color,
    alertStopIdx: Int,
    activeStopIdx: Int? = null,
    vehicleStamp: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 152.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        stops.forEachIndexed { idx, st ->
            val isAlert = idx == alertStopIdx
            val isKey = idx == 0 || idx == stops.lastIndex || st.line == MetroLineColor.INTERCHANGE || st.name.contains("Transfer", true) || isAlert
            val isCurrent = activeStopIdx == idx
            val stopColor = when {
                isAlert -> palette.red
                st.line == MetroLineColor.PURPLE -> palette.purple
                st.line == MetroLineColor.GREEN -> palette.green
                st.line == MetroLineColor.YELLOW -> Color(0xFFCA8A04)
                st.line == MetroLineColor.PINK -> Color(0xFFDB2777)
                st.line == MetroLineColor.INTERCHANGE -> palette.blue
                else -> fallbackColor
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) stopColor.copy(alpha = 0.14f) else Color.Transparent)
                    .padding(horizontal = if (isCurrent) 6.dp else 0.dp, vertical = if (isCurrent) 3.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCurrent && vehicleStamp != null) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(stopColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(vehicleStamp, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (isKey) 8.dp else 5.5.dp)
                            .clip(CircleShape)
                            .background(stopColor)
                    )
                }
                Text(
                    text = if (isAlert) "${st.name} (Alert)" else st.name,
                    color = if (isCurrent || isKey) palette.ink else palette.muted,
                    fontSize = 11.5.sp,
                    fontWeight = if (isCurrent || isKey) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent) {
                    Text("LIVE", color = stopColor, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InteractiveTimelineRow(
    palette: WakeMePalette,
    dotColor: Color,
    title: String,
    subtitle: String,
    value: String,
    isOrange: Boolean,
    isFocused: Boolean,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) dotColor.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = if (isFocused) 6.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                color = if (isOrange) palette.orange else palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ActiveSheetContent(
    palette: WakeMePalette,
    scenario: TripScenario,
    mode: TransitMode,
    motion: MotionPreset,
    activeStageIdx: Int,
    activeTripProgress: Float,
    autoSimRunning: Boolean,
    selectedBusIdx: Int,
    transitLeadStops: Int,
    roadLeadMeters: Int,
    ringStyle: RingStyle,
    onSelectStage: (Int) -> Unit,
    onProgressChange: (Float) -> Unit,
    onToggleSim: () -> Unit,
    onEnd: () -> Unit
) {
    val stages = activeStagesFor(scenario, mode, selectedBusIdx, transitLeadStops, roadLeadMeters, palette)
    val safeIdx = activeStageIdx.coerceIn(0, stages.lastIndex)
    val st = stages[safeIdx]
    val busOpt = scenario.busOptions[selectedBusIdx.coerceIn(0, scenario.busOptions.lastIndex)]
    val isTransitStage2 = mode != TransitMode.CAR && safeIdx == 1 && (mode == TransitMode.BUS || scenario.metroPlan != null)
    val stops = if (mode == TransitMode.METRO) (scenario.metroPlan?.allStops ?: emptyList()) else busOpt.stops
    val currentStopIdx = if (stops.isNotEmpty()) (activeTripProgress * stops.lastIndex).toInt().coerceIn(0, stops.lastIndex) else 0
    val alertStopIdx = if (stops.isNotEmpty()) (stops.size - 1 - transitLeadStops).coerceIn(0, stops.lastIndex) else 0

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stages.forEachIndexed { idx, s ->
                val selected = idx == safeIdx
                Surface(
                    onClick = { onSelectStage(idx) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        selected && s.isOrange -> palette.orange
                        selected -> palette.ctaBg
                        else -> palette.bg
                    }
                ) {
                    Text(
                        text = s.pillLabel,
                        color = when {
                            selected && s.isOrange -> Color.White
                            selected -> palette.ctaInk
                            else -> palette.muted
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("${st.badge} • ${ringStyle.label}", color = st.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = if (isTransitStage2 && stops.isNotEmpty()) "${(stops.size - 1 - currentStopIdx).coerceAtLeast(0)} left" else st.hero,
                color = palette.ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Text(
                text = if (isTransitStage2 && stops.isNotEmpty()) stops[currentStopIdx].name else st.eta,
                color = palette.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = activeTripProgress,
                onValueChange = onProgressChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).height(26.dp),
                colors = SliderDefaults.colors(
                    thumbColor = st.accentColor,
                    activeTrackColor = st.accentColor,
                    inactiveTrackColor = palette.bg
                )
            )
            Surface(
                onClick = onToggleSim,
                shape = CircleShape,
                color = palette.bg,
                border = BorderStroke(1.dp, palette.hairline)
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (autoSimRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (autoSimRunning) "Pause simulation" else "Play simulation",
                        tint = palette.ink,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (isTransitStage2 && stops.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            InlineStopList(
                stops = stops,
                palette = palette,
                fallbackColor = if (mode == TransitMode.METRO) palette.purple else palette.blue,
                alertStopIdx = alertStopIdx,
                activeStopIdx = currentStopIdx,
                vehicleStamp = if (mode == TransitMode.METRO) "M" else "B"
            )
        } else {
            Spacer(Modifier.height(4.dp))
            TimelineRow(palette, st.accentColor, st.nextTitle, st.nextSub, "", false)
        }
        Spacer(Modifier.height(10.dp))
        TactileCtaButton("End Commute", palette.red, Color.White, motion, onEnd)
    }
}

@Composable
private fun TimelineRow(
    palette: WakeMePalette,
    dotColor: Color,
    title: String,
    subtitle: String,
    value: String,
    isOrange: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value.isNotEmpty()) {
            Text(
                value,
                color = if (isOrange) palette.orange else palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun TactileCtaButton(
    text: String,
    bg: Color,
    ink: Color,
    motion: MotionPreset,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScaleFor(motion) else 1f,
        animationSpec = tween(motionDurationMs(motion).coerceAtMost(180)),
        label = "ctaScale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = ink),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class ActiveStageInfo(
    val pillLabel: String,
    val isOrange: Boolean,
    val badge: String,
    val accentColor: Color,
    val hero: String,
    val eta: String,
    val nextTitle: String,
    val nextSub: String
)

private fun activeStagesFor(
    scenario: TripScenario,
    mode: TransitMode,
    selectedBusIdx: Int,
    transitLeadStops: Int,
    roadLeadMeters: Int,
    p: WakeMePalette
): List<ActiveStageInfo> {
    val roadLabel = if (roadLeadMeters >= 1000) "${roadLeadMeters / 1000} km" else "${roadLeadMeters} m"
    return when (mode) {
        TransitMode.METRO -> {
            val m = scenario.metroPlan
            if (m == null) {
                listOf(ActiveStageInfo("Road Fallback", false, "NO DIRECT METRO", p.muted, scenario.carPlan.roadDistLabel, scenario.carPlan.etaLabel, scenario.destName, "Alert armed at $roadLabel"))
            } else {
                val alertIdx = (m.allStops.size - 1 - transitLeadStops).coerceIn(0, m.allStops.lastIndex)
                listOf(
                    ActiveStageInfo("1. To Metro", true, "STAGE 1 • TO BOARDING", p.orange, "${m.boardWalkMeters} m", "~${m.boardWalkMins} min walk", m.boardStation.name, "Auto-switches within 50m"),
                    ActiveStageInfo("2. Metro (${m.totalStopsCount})", false, "METRO • ${m.summaryLine.uppercase()}", p.purple, "${m.totalStopsCount} stops", "~22 min", "Alert: ${m.allStops[alertIdx].name}", "Rings $transitLeadStops ${if (transitLeadStops == 1) "stop" else "stops"} before ${m.exitStation.name}"),
                    ActiveStageInfo("3. Final Road", true, "STAGE 3 • FINAL APPROACH", p.orange, m.exitDistLabel, "~${m.exitMins} min", scenario.destName, "Gentle reminder fires at 150m")
                )
            }
        }
        TransitMode.BUS -> {
            val b = scenario.busOptions[selectedBusIdx.coerceIn(0, scenario.busOptions.lastIndex)]
            val alertIdx = (b.stops.size - 1 - transitLeadStops).coerceIn(0, b.stops.lastIndex)
            listOf(
                ActiveStageInfo("1. To Stop", true, "STAGE 1 • TO BUS POLE", p.orange, "${b.boardWalkMeters} m", "~${b.boardWalkMins} min walk", b.legs.first().boardStop, "Board ${b.leg1} • Auto-switches at 50m"),
                ActiveStageInfo("2. ${b.chainSummary}", false, "BMTC • ${b.legs.size} ${if (b.legs.size == 1) "BUS" else "BUSES"}", p.blue, b.stopsSummary, b.via, "Alert: ${b.stops[alertIdx].name}", "Rings $transitLeadStops ${if (transitLeadStops == 1) "stop" else "stops"} before exit"),
                ActiveStageInfo("3. Final Walk", true, "STAGE 3 • STOP TO DEST", p.orange, "${b.exitWalkMeters} m", "~${b.exitWalkMins} min walk", scenario.destName, "Within 150m final approach gate")
            )
        }
        TransitMode.CAR -> listOf(
            ActiveStageInfo("Live Road", false, "DIRECT ROAD • ${scenario.carPlan.viaLabel.uppercase()}", p.orange, scenario.carPlan.roadDistLabel, scenario.carPlan.etaLabel, "Alert at $roadLabel remaining", "Gentle reminder armed at $roadLabel along road")
        )
    }
}

private fun directionCueFor(
    screen: PreviewScreen,
    scenario: TripScenario,
    mode: TransitMode,
    activeStageIdx: Int,
    selectedBusIdx: Int,
    focusedLegStage: Int?,
    transitLeadStops: Int,
    roadLeadMeters: Int
): Triple<String, String, String> {
    val roadLabel = if (roadLeadMeters >= 1000) "${roadLeadMeters / 1000} km" else "${roadLeadMeters} m"
    val busOpt = scenario.busOptions[selectedBusIdx.coerceIn(0, scenario.busOptions.lastIndex)]
    val metro = scenario.metroPlan

    if (screen == PreviewScreen.SETUP && focusedLegStage != null) {
        return when (focusedLegStage) {
            0 -> Triple("↗", "Stage 1: Road to Boarding", if (mode == TransitMode.METRO && metro != null) "${metro.boardWalkMeters}m to ${metro.boardStation.name}" else "${busOpt.boardWalkMeters}m to ${busOpt.legs.first().boardStop}")
            1 -> Triple(if (mode == TransitMode.METRO) "M" else "B", "Stage 2: Transit Corridor", if (mode == TransitMode.METRO && metro != null) "${metro.summaryLine} (${metro.totalStopsCount} stops)" else "${busOpt.chainSummary} (${busOpt.stopsSummary})")
            else -> Triple("↑", "Stage 3: Final Road Path", if (mode == TransitMode.METRO && metro != null) "${metro.exitDistLabel} to ${scenario.destName}" else "${busOpt.exitWalkMeters}m walk to ${scenario.destName}")
        }
    }
    if (screen == PreviewScreen.ACTIVE) {
        val list = when (mode) {
            TransitMode.METRO -> if (metro != null) listOf(
                Triple("↗", "Head ${metro.boardWalkMeters}m to ${metro.boardStation.name}", "Switches to Metro tracking at 50m"),
                Triple("M", "${metro.summaryLine} • Alert $transitLeadStops before", "Live stop tracking active"),
                Triple("↑", "Continue ${metro.exitDistLabel} to ${scenario.destName}", "Final road leg • Alerts at 150m")
            ) else listOf(Triple("↑", "${scenario.carPlan.roadDistLabel} to ${scenario.destName}", "Alert armed at $roadLabel"))
            TransitMode.BUS -> listOf(
                Triple("↗", "Walk ${busOpt.boardWalkMeters}m to ${busOpt.legs.first().boardStop}", "Board ${busOpt.leg1}"),
                Triple("B", "${busOpt.chainSummary} • Alert $transitLeadStops before", busOpt.via),
                Triple("↑", "Walk ${busOpt.exitWalkMeters}m to ${scenario.destName}", "Arriving at destination")
            )
            TransitMode.CAR -> listOf(Triple("↑", "${scenario.carPlan.roadDistLabel} via ${scenario.carPlan.viaLabel}", "Gentle reminder triggers at $roadLabel"))
        }
        return list[activeStageIdx.coerceIn(0, list.lastIndex)]
    }
    return when (mode) {
        TransitMode.METRO -> if (metro != null) Triple("↗", "Walk ${metro.boardWalkMeters}m to ${metro.boardStation.name}", "${metro.summaryLine} • ~${metro.boardWalkMins} min walk") else Triple("↑", "No Direct Metro", "Switch to Bus or Direct Road")
        TransitMode.BUS -> Triple("↗", "Walk ${busOpt.boardWalkMeters}m to ${busOpt.legs.first().boardStop}", "Board ${busOpt.chainSummary}")
        TransitMode.CAR -> Triple("↑", "${scenario.carPlan.roadDistLabel} via ${scenario.carPlan.viaLabel}", "Real-road tracking • Alert at $roadLabel")
    }
}
