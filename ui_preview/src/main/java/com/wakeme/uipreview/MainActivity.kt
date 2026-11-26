package com.wakeme.uipreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2

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

private enum class ModalKind { NONE, SAVED, HISTORY }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WakeMeUiLabApp() {
    var screen by remember { mutableStateOf(PreviewScreen.LANDING) }
    var mode by remember { mutableStateOf(TransitMode.METRO) }
    var isDark by remember { mutableStateOf(false) }
    var scheme by remember { mutableStateOf(ColorSchemePreset.SIGNAL) }
    var motion by remember { mutableStateOf(MotionPreset.EMIL) }
    var selectedBusIdx by remember { mutableIntStateOf(0) }
    var activeStageIdx by remember { mutableIntStateOf(0) }
    var modalKind by remember { mutableStateOf(ModalKind.NONE) }
    var focusedLegStage by remember { mutableStateOf<Int?>(null) } // Long-press 0, 1, or 2
    var userZoom by remember { mutableFloatStateOf(1.15f) }
    var selectedDest by remember { mutableStateOf(PreviewFixtures.savedPlaces.first()) }

    val p = paletteFor(scheme, isDark)
    val animSpec = motionSpecFor(motion)
    val offsetSpec = motionOffsetSpecFor(motion)
    val durMs = motionDurationMs(motion)

    BackHandler(enabled = modalKind != ModalKind.NONE || focusedLegStage != null || screen != PreviewScreen.LANDING) {
        when {
            modalKind != ModalKind.NONE -> modalKind = ModalKind.NONE
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
        // 1. Single Persistent Interactive Vector Map Canvas (supports Pinch Zoom + LOD + Long-Press Leg Focus + Moving Vehicle)
        PersistentVectorMapCanvas(
            palette = p,
            screen = screen,
            mode = mode,
            motion = motion,
            focusedLegStage = focusedLegStage,
            activeStageIdx = activeStageIdx,
            userZoom = userZoom,
            onZoomChange = { delta -> userZoom = (userZoom * delta).coerceIn(0.65f, 2.4f) },
            animSpec = animSpec
        )

        // 2. Top Safe-Area Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabDropdownBar(
                palette = p,
                isDark = isDark,
                scheme = scheme,
                motion = motion,
                onToggleDark = { isDark = !isDark },
                onSelectScheme = { scheme = it },
                onSelectMotion = { motion = it }
            )

            AnimatedVisibility(
                visible = screen == PreviewScreen.LANDING,
                enter = fadeIn(tween(durMs)) + slideInVertically(offsetSpec) { -it / 4 },
                exit = fadeOut(tween(durMs / 2)) + slideOutVertically(offsetSpec) { -it / 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            focusedLegStage = null
                            screen = PreviewScreen.SETUP
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = p.surface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, p.hairline),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = p.blue, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (selectedDest.isNamedPlace) selectedDest.title else selectedDest.coordsLabel,
                                color = p.muted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickPillChip("Saved Places", "3", p) { modalKind = ModalKind.SAVED }
                        QuickPillChip("History", "3", p) { modalKind = ModalKind.HISTORY }
                    }
                }
            }

            AnimatedVisibility(
                visible = screen != PreviewScreen.LANDING,
                enter = fadeIn(tween(durMs)) + slideInVertically(offsetSpec) { -it / 4 },
                exit = fadeOut(tween(durMs / 2)) + slideOutVertically(offsetSpec) { -it / 4 }
            ) {
                val (cueArrow, cueMain, cueSub) = directionCueFor(screen, mode, activeStageIdx, selectedBusIdx, focusedLegStage)
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

        // Right-side Map Zoom Controls (makes testing LOD: Polyline -> Dots -> English Names easy)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = { userZoom = (userZoom * 1.25f).coerceAtMost(2.4f) },
                shape = CircleShape,
                color = p.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, p.hairline),
                shadowElevation = 4.dp
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = p.ink, modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                onClick = { userZoom = (userZoom / 1.25f).coerceAtLeast(0.65f) },
                shape = CircleShape,
                color = p.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, p.hairline),
                shadowElevation = 4.dp
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = p.ink, modifier = Modifier.size(18.dp))
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
                            dest = selectedDest,
                            motion = motion,
                            onNext = {
                                focusedLegStage = null
                                screen = PreviewScreen.SETUP
                            }
                        )
                        PreviewScreen.SETUP -> SetupSheetContent(
                            palette = p,
                            mode = mode,
                            motion = motion,
                            selectedBusIdx = selectedBusIdx,
                            focusedLegStage = focusedLegStage,
                            onSelectMode = {
                                mode = it
                                activeStageIdx = 0
                                focusedLegStage = null
                            },
                            onSelectBus = { selectedBusIdx = it },
                            onLongPressLeg = { stage ->
                                focusedLegStage = if (focusedLegStage == stage) null else stage
                            },
                            onStart = {
                                focusedLegStage = null
                                activeStageIdx = 0
                                screen = PreviewScreen.ACTIVE
                            }
                        )
                        PreviewScreen.ACTIVE -> ActiveSheetContent(
                            palette = p,
                            mode = mode,
                            motion = motion,
                            activeStageIdx = activeStageIdx,
                            selectedBusIdx = selectedBusIdx,
                            onSelectStage = { activeStageIdx = it },
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

        // 4. Saved Places & History Overlay Sheet (uses motion preset!)
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
                            text = if (modalKind == ModalKind.SAVED) "Saved Places" else "Recent Commutes",
                            color = p.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = p.hairline)

                    val items = if (modalKind == ModalKind.SAVED) PreviewFixtures.savedPlaces else PreviewFixtures.historyItems
                    items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDest = item
                                    mode = item.mode
                                    activeStageIdx = 0
                                    focusedLegStage = null
                                    modalKind = ModalKind.NONE
                                    screen = PreviewScreen.SETUP
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (item.isOrange) p.orange.copy(alpha = 0.14f) else p.bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    item.badge,
                                    color = if (item.isOrange) p.orange else p.blue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(item.tag, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (idx < items.lastIndex) HorizontalDivider(color = p.hairline)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun LabDropdownBar(
    palette: WakeMePalette,
    isDark: Boolean,
    scheme: ColorSchemePreset,
    motion: MotionPreset,
    onToggleDark: () -> Unit,
    onSelectScheme: (ColorSchemePreset) -> Unit,
    onSelectMotion: (MotionPreset) -> Unit
) {
    var schemeOpen by remember { mutableStateOf(false) }
    var motionOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggleDark,
            shape = RoundedCornerShape(999.dp),
            color = palette.cueBg,
            border = BorderStroke(1.dp, palette.hairline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = null,
                    tint = palette.cueInk,
                    modifier = Modifier.size(14.dp)
                )
                Text(if (isDark) "Dark" else "Light", color = palette.cueInk, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box {
            Surface(
                onClick = { schemeOpen = true },
                shape = RoundedCornerShape(999.dp),
                color = palette.cueBg,
                border = BorderStroke(1.dp, palette.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(scheme.label, color = palette.cueInk, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.cueInk, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = schemeOpen, onDismissRequest = { schemeOpen = false }) {
                ColorSchemePreset.values().forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label, fontWeight = if (item == scheme) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onSelectScheme(item)
                            schemeOpen = false
                        }
                    )
                }
            }
        }

        Box {
            Surface(
                onClick = { motionOpen = true },
                shape = RoundedCornerShape(999.dp),
                color = palette.cueBg,
                border = BorderStroke(1.dp, palette.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(motion.label, color = palette.cueInk, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.cueInk, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = motionOpen, onDismissRequest = { motionOpen = false }) {
                MotionPreset.values().forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label, fontWeight = if (item == motion) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onSelectMotion(item)
                            motionOpen = false
                        }
                    )
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
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = palette.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.bg)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(count, color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PersistentVectorMapCanvas(
    palette: WakeMePalette,
    screen: PreviewScreen,
    mode: TransitMode,
    motion: MotionPreset,
    focusedLegStage: Int?,
    activeStageIdx: Int,
    userZoom: Float,
    onZoomChange: (Float) -> Unit,
    animSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>
) {
    val targetScale = when {
        focusedLegStage == 0 -> 1.85f
        focusedLegStage == 1 -> 1.25f
        focusedLegStage == 2 -> 1.75f
        screen == PreviewScreen.LANDING -> 1f
        screen == PreviewScreen.SETUP -> 1.08f
        else -> 1.15f
    } * userZoom

    val mapScale by animateFloatAsState(targetScale, animationSpec = animSpec, label = "mapScale")
    val mapRotation by animateFloatAsState(
        targetValue = if (screen == PreviewScreen.ACTIVE && focusedLegStage == null) -10f else 0f,
        animationSpec = animSpec,
        label = "mapRot"
    )
    val mapShiftX by animateFloatAsState(
        targetValue = when (focusedLegStage) {
            0 -> -220f
            2 -> 240f
            else -> 0f
        },
        animationSpec = animSpec,
        label = "mapShiftX"
    )
    val mapShiftY by animateFloatAsState(
        targetValue = when {
            focusedLegStage == 0 -> -40f
            focusedLegStage == 2 -> 190f
            screen == PreviewScreen.LANDING -> 0f
            screen == PreviewScreen.SETUP -> -80f
            else -> -110f
        },
        animationSpec = animSpec,
        label = "mapShiftY"
    )
    val coneAngle by animateFloatAsState(
        targetValue = when (screen) {
            PreviewScreen.LANDING -> -42f
            PreviewScreen.SETUP -> -32f
            PreviewScreen.ACTIVE -> -54f
        },
        animationSpec = animSpec,
        label = "coneAngle"
    )

    val vehicleTransition = rememberInfiniteTransition(label = "vehicle")
    val vehicleProgress by vehicleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "vehProg"
    )
    val pulseRadius by vehicleTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "pulseR"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) onZoomChange(zoom)
                }
            }
            .graphicsLayer {
                scaleX = mapScale
                scaleY = mapScale
                rotationZ = mapRotation
                translationX = mapShiftX
                translationY = mapShiftY
            }
    ) {
        val w = size.width
        val h = size.height
        val px = { x: Float -> w * x }
        val py = { y: Float -> h * y }

        // Subtle road grid
        listOf(0.24f, 0.42f, 0.62f).forEach { y ->
            drawLine(palette.mapRoadMajor, Offset(0f, py(y)), Offset(w, py(y)), strokeWidth = 18f)
        }
        listOf(0.22f, 0.52f, 0.78f).forEach { x ->
            drawLine(palette.mapRoadMajor, Offset(px(x), 0f), Offset(px(x), h), strokeWidth = 18f)
        }
        drawLine(palette.mapRoadMinor, Offset(px(0.06f), py(0.14f)), Offset(px(0.92f), py(0.45f)), strokeWidth = 10f)

        val originPt = Offset(px(0.84f), py(0.44f))
        val boardMetroPt = Offset(px(0.76f), py(0.42f))
        val majesticPt = Offset(px(0.41f), py(0.38f))
        val madavaraPt = Offset(px(0.21f), py(0.19f))
        val destPt = Offset(px(0.14f), py(0.14f))

        val showStage1 = focusedLegStage == null || focusedLegStage == 0
        val showStage2 = focusedLegStage == null || focusedLegStage == 1
        val showStage3 = focusedLegStage == null || focusedLegStage == 2

        val showMetro = mode == TransitMode.METRO
        val metroAlpha = if (showMetro) 1f else 0.16f

        if (showStage2) {
            val purplePath = Path().apply {
                moveTo(boardMetroPt.x, boardMetroPt.y)
                lineTo(px(0.52f), py(0.42f))
                lineTo(majesticPt.x, majesticPt.y)
            }
            drawPath(purplePath, palette.purple.copy(alpha = metroAlpha), style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            val greenPath = Path().apply {
                moveTo(majesticPt.x, majesticPt.y)
                lineTo(px(0.30f), py(0.28f))
                lineTo(madavaraPt.x, madavaraPt.y)
            }
            drawPath(greenPath, palette.green.copy(alpha = metroAlpha), style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Stage 1 & Stage 3 orange paths + directional triangular arrows
        if (showMetro || mode == TransitMode.BUS) {
            val boardTarget = if (showMetro) boardMetroPt else Offset(px(0.79f), py(0.41f))
            val exitSource = if (showMetro) madavaraPt else Offset(px(0.17f), py(0.16f))
            if (showStage1) {
                drawLine(palette.orange, originPt, boardTarget, strokeWidth = 8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                drawPathArrow(originPt, boardTarget, 0.5f, palette.orange)
            }
            if (showStage3) {
                drawLine(palette.orange, exitSource, destPt, strokeWidth = 8f, cap = StrokeCap.Round)
                drawPathArrow(exitSource, destPt, 0.5f, palette.orange)
            }
        }

        // BMTC / Direct Road primary path
        if (!showMetro && showStage2) {
            val busStart = if (mode == TransitMode.BUS) Offset(px(0.79f), py(0.41f)) else originPt
            val busEnd = if (mode == TransitMode.BUS) Offset(px(0.17f), py(0.16f)) else destPt
            val roadPts = listOf(busStart, Offset(px(0.66f), py(0.34f)), Offset(px(0.42f), py(0.28f)), Offset(px(0.26f), py(0.21f)), busEnd)
            val roadPath = Path().apply {
                moveTo(roadPts.first().x, roadPts.first().y)
                roadPts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(roadPath, if (mode == TransitMode.BUS) palette.blue else palette.orange, style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Zoom-based LOD for Stations/Stops:
        // < 0.95f: polyline only; 0.95f..1.34f: dots only; >= 1.35f: dots + English names
        val showDots = mapScale >= 0.95f
        val showNames = mapScale >= 1.35f

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 22f
            color = palette.ink.toArgb()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val metroNodes = listOf(
            Triple(boardMetroPt, palette.purple, "Singayyanapalya"),
            Triple(majesticPt, palette.blue, "Majestic"),
            Triple(madavaraPt, palette.green, "Madavara")
        )
        if (showDots && showStage2) {
            metroNodes.forEach { (pt, col, name) ->
                drawCircle(Color.White, 10f, pt)
                drawCircle(col, 6.5f, pt)
                if (showNames) {
                    drawContext.canvas.nativeCanvas.drawText(name, pt.x + 14f, pt.y + 6f, labelPaint)
                }
            }
        }
        if (showStage3) {
            drawCircle(Color.White, 12f, destPt)
            drawCircle(palette.red, 8f, destPt)
            if (showNames) {
                drawContext.canvas.nativeCanvas.drawText("Acharya College", destPt.x + 14f, destPt.y + 6f, labelPaint)
            }
        }

        // Live Moving White Vehicle Icon during Active Trip
        if (screen == PreviewScreen.ACTIVE) {
            val vehPos = when {
                mode == TransitMode.METRO && activeStageIdx == 1 -> interpolatePolyline(
                    listOf(boardMetroPt, Offset(px(0.52f), py(0.42f)), majesticPt, Offset(px(0.30f), py(0.28f)), madavaraPt),
                    vehicleProgress
                )
                mode == TransitMode.BUS && activeStageIdx == 1 -> interpolatePolyline(
                    listOf(Offset(px(0.79f), py(0.41f)), Offset(px(0.66f), py(0.34f)), Offset(px(0.42f), py(0.28f)), Offset(px(0.26f), py(0.21f)), Offset(px(0.17f), py(0.16f))),
                    vehicleProgress
                )
                mode == TransitMode.CAR -> interpolatePolyline(
                    listOf(originPt, Offset(px(0.66f), py(0.34f)), Offset(px(0.42f), py(0.28f)), Offset(px(0.26f), py(0.21f)), destPt),
                    vehicleProgress
                )
                else -> null
            }
            if (vehPos != null) {
                drawWhiteVehicleBadge(vehPos, mode)
            }
        }

        // User Puck + Compass Heading Cone
        if (showStage1 || screen == PreviewScreen.LANDING) {
            rotate(coneAngle, pivot = originPt) {
                val conePath = Path().apply {
                    moveTo(originPt.x, originPt.y)
                    lineTo(originPt.x - 42f, originPt.y - 96f)
                    lineTo(originPt.x + 42f, originPt.y - 96f)
                    close()
                }
                drawPath(
                    conePath,
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, palette.blue.copy(alpha = 0.34f)),
                        startY = originPt.y - 96f,
                        endY = originPt.y
                    )
                )
            }
            if (motion == MotionPreset.ELASTIC) {
                drawCircle(palette.blue.copy(alpha = 0.25f), pulseRadius, originPt)
            }
            drawCircle(Color.White, 15f, originPt)
            drawCircle(palette.blue, 10f, originPt)
        }
    }
}

private fun DrawScope.drawPathArrow(start: Offset, end: Offset, t: Float, color: Color) {
    val cx = start.x + (end.x - start.x) * t
    val cy = start.y + (end.y - start.y) * t
    val angleDeg = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat() + 90f
    rotate(angleDeg, pivot = Offset(cx, cy)) {
        val tri = Path().apply {
            moveTo(cx, cy - 11f)
            lineTo(cx - 8f, cy + 7f)
            lineTo(cx + 8f, cy + 7f)
            close()
        }
        drawPath(tri, Color.White)
        drawPath(tri, color, style = Stroke(2.5f))
    }
}

private fun DrawScope.drawWhiteVehicleBadge(pos: Offset, mode: TransitMode) {
    // Soft dark halo so white vehicle stays crisp in both Light and Dark modes
    drawCircle(Color(0x550F172A), radius = 18f, center = pos)
    drawCircle(Color.White, radius = 14f, center = pos)
    drawCircle(Color(0xFF0F172A), radius = 14f, center = pos, style = Stroke(2.5f))
    // Inner glyph silhouette (Metro / Bus / Car)
    val glyphColor = Color(0xFF0F172A)
    when (mode) {
        TransitMode.METRO -> {
            drawRoundRect(glyphColor, topLeft = Offset(pos.x - 5.5f, pos.y - 7f), size = Size(11f, 12f), cornerRadius = CornerRadius(3f, 3f))
            drawRect(Color.White, topLeft = Offset(pos.x - 3.5f, pos.y - 4.5f), size = Size(7f, 4f))
        }
        TransitMode.BUS -> {
            drawRoundRect(glyphColor, topLeft = Offset(pos.x - 6f, pos.y - 6.5f), size = Size(12f, 12f), cornerRadius = CornerRadius(2.5f, 2.5f))
            drawRect(Color.White, topLeft = Offset(pos.x - 4f, pos.y - 4f), size = Size(8f, 4f))
        }
        TransitMode.CAR -> {
            drawRoundRect(glyphColor, topLeft = Offset(pos.x - 7f, pos.y - 4f), size = Size(14f, 8f), cornerRadius = CornerRadius(3f, 3f))
            drawCircle(glyphColor, radius = 2.2f, center = Offset(pos.x - 4f, pos.y + 4.5f))
            drawCircle(glyphColor, radius = 2.2f, center = Offset(pos.x + 4f, pos.y + 4.5f))
        }
    }
}

private fun interpolatePolyline(pts: List<Offset>, t: Float): Offset {
    if (pts.isEmpty()) return Offset.Zero
    if (pts.size == 1) return pts.first()
    val segCount = pts.size - 1
    val scaled = (t.coerceIn(0f, 0.9999f)) * segCount
    val idx = scaled.toInt().coerceIn(0, segCount - 1)
    val localT = scaled - idx
    val a = pts[idx]
    val b = pts[idx + 1]
    return Offset(a.x + (b.x - a.x) * localT, a.y + (b.y - a.y) * localT)
}

@Composable
private fun LandingSheetContent(
    palette: WakeMePalette,
    dest: PlaceListItem,
    motion: MotionPreset,
    onNext: () -> Unit
) {
    val primaryLabel = if (dest.isNamedPlace) dest.title else dest.coordsLabel
    val secondaryLabel = if (dest.isNamedPlace) {
        "${dest.distanceLabel} from your location"
    } else {
        "${dest.distanceLabel} from your location • Dropped Pin"
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
                text = if (dest.isNamedPlace) "Selected Destination" else "Pinned Coordinates",
                color = palette.muted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = primaryLabel,
                color = palette.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = secondaryLabel,
                color = palette.orange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressScaleFor(motion) else 1f,
            animationSpec = tween(motionDurationMs(motion).coerceAtMost(160)),
            label = "goScale"
        )
        Surface(
            onClick = onNext,
            interactionSource = interactionSource,
            shape = CircleShape,
            color = palette.ctaBg,
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
                    contentDescription = "Continue to Trip Setup",
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
    mode: TransitMode,
    motion: MotionPreset,
    selectedBusIdx: Int,
    focusedLegStage: Int?,
    onSelectMode: (TransitMode) -> Unit,
    onSelectBus: (Int) -> Unit,
    onLongPressLeg: (Int) -> Unit,
    onStart: () -> Unit
) {
    var stopsExpanded by remember(mode, selectedBusIdx) { mutableStateOf(false) }
    val busOpt = PreviewFixtures.busOptions[selectedBusIdx]
    val title = when (mode) {
        TransitMode.METRO -> "Acharya College • 27 Metro Stops"
        TransitMode.BUS -> "Acharya College • ${busOpt.leg1} ➔ ${busOpt.leg2}"
        TransitMode.CAR -> "Acharya College • Direct Road"
    }
    val durMs = motionDurationMs(motion)

    Column {
        Text("Phoenix Marketcity → Acharya Institute • Long-press any step to isolate map", color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

        // Uniform BMTC Route Cards
        if (mode == TransitMode.BUS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewFixtures.busOptions.forEachIndexed { idx, opt ->
                    val sel = idx == selectedBusIdx
                    Surface(
                        onClick = { onSelectBus(idx) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) palette.blue.copy(alpha = 0.12f) else palette.bg,
                        border = BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) palette.blue else palette.hairline)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(184.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(palette.blue)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(opt.leg1, color = palette.badgeInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    Text("➔", color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(palette.blue)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(opt.leg2, color = palette.badgeInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(opt.stopsSummary, color = palette.ink, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${opt.via} • ${opt.freq}", color = palette.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        when (mode) {
            TransitMode.METRO -> {
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "1. Reach Singayyanapalya Metro",
                    subtitle = "Hold to view path • Switches at 50m",
                    value = "466m • 6m",
                    isOrange = true,
                    isFocused = focusedLegStage == 0,
                    onClick = { onLongPressLeg(0) },
                    onLongClick = { onLongPressLeg(0) }
                )
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.purple,
                    title = "2. Purple ➔ Green (via Majestic)",
                    subtitle = if (stopsExpanded) "Tap to hide stops • Hold to isolate map" else "Tap to view all 27 stops • Hold for map",
                    value = "27 stops",
                    isOrange = false,
                    isFocused = focusedLegStage == 1,
                    trailingIcon = if (stopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    onClick = { stopsExpanded = !stopsExpanded },
                    onLongClick = { onLongPressLeg(1) }
                )
                AnimatedVisibility(
                    visible = stopsExpanded,
                    enter = expandVertically(tween(durMs)) + fadeIn(tween(durMs)),
                    exit = shrinkVertically(tween(durMs)) + fadeOut(tween(durMs))
                ) {
                    InlineStopList(PreviewFixtures.metroStopsSample, palette, palette.purple)
                }
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "3. Madavara Metro ➔ Acharya College",
                    subtitle = "Hold to view final road path • Alerts at 150m",
                    value = "3.3 km • 10m",
                    isOrange = true,
                    isFocused = focusedLegStage == 2,
                    onClick = { onLongPressLeg(2) },
                    onLongClick = { onLongPressLeg(2) }
                )
            }
            TransitMode.BUS -> {
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "1. Walk to Singaianapalya Phoenix",
                    subtitle = "Hold to view pole path • Switches at 50m",
                    value = "236m • 3m",
                    isOrange = true,
                    isFocused = focusedLegStage == 0,
                    onClick = { onLongPressLeg(0) },
                    onLongClick = { onLongPressLeg(0) }
                )
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.blue,
                    title = "2. ${busOpt.leg1} ➔ ${busOpt.leg2}",
                    subtitle = if (stopsExpanded) "Tap to hide stops • Hold to isolate map" else "Tap to view stops • ${busOpt.via}",
                    value = busOpt.stopsSummary,
                    isOrange = false,
                    isFocused = focusedLegStage == 1,
                    trailingIcon = if (stopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    onClick = { stopsExpanded = !stopsExpanded },
                    onLongClick = { onLongPressLeg(1) }
                )
                AnimatedVisibility(
                    visible = stopsExpanded,
                    enter = expandVertically(tween(durMs)) + fadeIn(tween(durMs)),
                    exit = shrinkVertically(tween(durMs)) + fadeOut(tween(durMs))
                ) {
                    InlineStopList(busOpt.stopNames, palette, palette.blue)
                }
                HorizontalDivider(color = palette.hairline)
                InteractiveTimelineRow(
                    palette = palette,
                    dotColor = palette.orange,
                    title = "3. Exit at Acharya Institute of Tech",
                    subtitle = "Hold to view final walk • Alerts at 150m",
                    value = "134m • 2m",
                    isOrange = true,
                    isFocused = focusedLegStage == 2,
                    onClick = { onLongPressLeg(2) },
                    onLongClick = { onLongPressLeg(2) }
                )
            }
            TransitMode.CAR -> {
                TimelineRow(palette, palette.blue, "Real-Road Route (25.0 km aerial)", "Tracks along road polyline; ignores flyovers", "~32 km • 58m", false)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.orange, "Road Alert Threshold", "Gentle reminder fires when 1 km remains", "1 km", true)
            }
        }

        Spacer(Modifier.height(10.dp))
        val ctaText = when (mode) {
            TransitMode.METRO -> "Start Metro Journey (1 stop before)"
            TransitMode.BUS -> "Start Bus Journey (1 stop before)"
            TransitMode.CAR -> "Start Road Alarm (1 km)"
        }
        TactileCtaButton(ctaText, palette.ctaBg, palette.ctaInk, motion, onStart)
    }
}

@Composable
private fun InlineStopList(stops: List<String>, palette: WakeMePalette, lineColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 148.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        stops.forEachIndexed { idx, name ->
            val isKey = idx == 0 || idx == stops.lastIndex || name.contains("Change", true) || name.contains("Transfer", true) || name.contains("Alert", true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(if (isKey) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (name.contains("Alert", true)) palette.red else lineColor)
                )
                Text(
                    text = name,
                    color = if (isKey) palette.ink else palette.muted,
                    fontSize = 11.5.sp,
                    fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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
    mode: TransitMode,
    motion: MotionPreset,
    activeStageIdx: Int,
    selectedBusIdx: Int,
    onSelectStage: (Int) -> Unit,
    onEnd: () -> Unit
) {
    val stages = activeStagesFor(mode, selectedBusIdx, palette)
    val safeIdx = activeStageIdx.coerceIn(0, stages.lastIndex)
    val st = stages[safeIdx]

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
        Text(st.badge, color = st.accentColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = st.hero,
                color = palette.ink,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Text(
                text = st.eta,
                color = palette.muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Spacer(Modifier.height(8.dp))
        val animatedProgress by animateFloatAsState(
            targetValue = st.progress,
            animationSpec = motionSpecFor(motion),
            label = "railProg"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(st.accentColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        TimelineRow(palette, st.accentColor, st.nextTitle, st.nextSub, "", false)
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
        Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ActiveStageInfo(
    val pillLabel: String,
    val isOrange: Boolean,
    val badge: String,
    val accentColor: Color,
    val hero: String,
    val eta: String,
    val progress: Float,
    val nextTitle: String,
    val nextSub: String
)

private fun activeStagesFor(mode: TransitMode, selectedBusIdx: Int, p: WakeMePalette): List<ActiveStageInfo> = when (mode) {
    TransitMode.METRO -> listOf(
        ActiveStageInfo("1. To Metro", true, "STAGE 1 • ROAD TO BOARDING", p.orange, "466 m", "~6 min walk", 0.18f, "Singayyanapalya Metro", "Auto-switches to Purple Line within 50m"),
        ActiveStageInfo("2. Metro (27)", false, "PURPLE ➔ GREEN • VIA MAJESTIC", p.purple, "12 stops", "52 km/h • ~22 min", 0.48f, "Change at Majestic (12th stop)", "Interchange alert 1 stop before at Sir M. Visveshwaraya"),
        ActiveStageInfo("3. Final Road", true, "STAGE 3 • MADAVARA TO ACHARYA", p.orange, "3.3 km", "28 km/h • ~10 min", 0.86f, "Acharya Institute of Technology", "Gentle reminder fires at 150m along road")
    )
    TransitMode.BUS -> {
        val b = PreviewFixtures.busOptions[selectedBusIdx]
        listOf(
            ActiveStageInfo("1. To Stop", true, "STAGE 1 • ROAD TO BUS POLE", p.orange, "236 m", "~3 min walk", 0.15f, "Singaianapalya Phoenix (Westbound)", "Board ${b.leg1} • Auto-switches within 50m"),
            ActiveStageInfo("2. ${b.leg1}➔${b.leg2}", false, "BMTC ${b.leg1} ➔ ${b.leg2} • 1 TRANSFER", p.blue, "${b.leg1Stops} stops", "24 km/h • ~54 min", 0.42f, "Transfer at ${b.hub} → ${b.leg2}", "Alert 1 stop before ${b.hub}, then ${b.leg2Stops} stops"),
            ActiveStageInfo("3. Final Walk", true, "STAGE 3 • STOP TO CAMPUS", p.orange, "134 m", "~2 min walk", 0.95f, "Acharya Institute of Technology", "Within 150m final approach gate")
        )
    }
    TransitMode.CAR -> listOf(
        ActiveStageInfo("Live Road Tracking", false, "DIRECT ROAD • CAB / AUTO", p.blue, "14.2 km", "33 km/h • ~26 min", 0.55f, "Approaching Hebbal / Tumkur Rd", "Gentle reminder armed at 1 km along road")
    )
}

private fun directionCueFor(
    screen: PreviewScreen,
    mode: TransitMode,
    activeStageIdx: Int,
    selectedBusIdx: Int,
    focusedLegStage: Int?
): Triple<String, String, String> {
    if (screen == PreviewScreen.SETUP && focusedLegStage != null) {
        return when (focusedLegStage) {
            0 -> Triple("↗", "Stage 1: Road to Boarding", if (mode == TransitMode.METRO) "466m to Singayyanapalya Metro" else "236m to Singaianapalya Phoenix")
            1 -> Triple(if (mode == TransitMode.METRO) "M" else "B", "Stage 2: Transit Corridor", if (mode == TransitMode.METRO) "Purple ➔ Green via Majestic (27 stops)" else "${PreviewFixtures.busOptions[selectedBusIdx].leg1} ➔ ${PreviewFixtures.busOptions[selectedBusIdx].leg2}")
            else -> Triple("↑", "Stage 3: Final Road Path", if (mode == TransitMode.METRO) "3.3 km from Madavara to Acharya" else "134m walk to Acharya Campus")
        }
    }
    if (screen == PreviewScreen.ACTIVE) {
        val list = when (mode) {
            TransitMode.METRO -> listOf(
                Triple("↗", "Head West 466m to Singayyanapalya", "ITPL Main Rd • Switches to ride at 50m"),
                Triple("M", "Purple Line • Next: KR Pura", "Switch to Green Line (Madavara) at Majestic"),
                Triple("↑", "Continue 3.3 km on Hesaraghatta Rd", "Final road leg • Alerts at 150m remaining")
            )
            TransitMode.BUS -> {
                val b = PreviewFixtures.busOptions[selectedBusIdx]
                listOf(
                    Triple("↗", "Walk 236m to Singaianapalya Phoenix", "Westbound pole toward KR Market / Majestic"),
                    Triple("B", "Bus ${b.leg1} • ${b.leg1Stops} stops to ${b.hub}", "Then board ${b.leg2} to Acharya Institute"),
                    Triple("↑", "Walk 134m into Acharya Campus", "Arriving at destination")
                )
            }
            TransitMode.CAR -> listOf(Triple("↑", "14.2 km remaining along road", "Gentle reminder triggers at 1 km"))
        }
        return list[activeStageIdx.coerceIn(0, list.lastIndex)]
    }
    return when (mode) {
        TransitMode.METRO -> Triple("↗", "Walk 466m to Singayyanapalya Metro", "Purple Line toward Challaghatta • ~6 min walk")
        TransitMode.BUS -> Triple("↗", "Walk 236m to Singaianapalya Phoenix", "Board ${PreviewFixtures.busOptions[selectedBusIdx].leg1} • Towards Majestic")
        TransitMode.CAR -> Triple("↑", "~32.0 km via ORR & Tumkur Rd", "Real-road tracking • Gentle reminder at 1 km")
    }
}
