package com.wakeme.uipreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    val p = paletteFor(scheme, isDark)
    val animSpec = motionSpecFor(motion)

    BackHandler(enabled = modalKind != ModalKind.NONE || screen != PreviewScreen.LANDING) {
        when {
            modalKind != ModalKind.NONE -> modalKind = ModalKind.NONE
            screen == PreviewScreen.ACTIVE -> screen = PreviewScreen.SETUP
            screen == PreviewScreen.SETUP -> screen = PreviewScreen.LANDING
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.mapBg)
    ) {
        // 1. Single Persistent Vector-Style Map Canvas
        PersistentVectorMapCanvas(
            palette = p,
            screen = screen,
            mode = mode,
            motion = motion,
            animSpec = animSpec
        )

        // 2. Top Safe-Area Controls (Lab Dropdowns + Landing Search OR Direction Cue)
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
                enter = fadeIn(tween(180)) + slideInVertically { -it / 4 },
                exit = fadeOut(tween(140)) + slideOutVertically { -it / 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { screen = PreviewScreen.SETUP },
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
                                text = "Acharya College, Soladevanahalli",
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
                enter = fadeIn(tween(200)) + slideInVertically { -it / 4 },
                exit = fadeOut(tween(140)) + slideOutVertically { -it / 4 }
            ) {
                val (cueArrow, cueMain, cueSub) = directionCueFor(screen, mode, activeStageIdx, selectedBusIdx)
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
                    }
                }
            }
        }

        // 3. Persistent Spring Bottom Sheet
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
                        (fadeIn(tween(200)) + slideInVertically { it / 8 }) togetherWith fadeOut(tween(140))
                    },
                    label = "sheetContent"
                ) { current ->
                    when (current) {
                        PreviewScreen.LANDING -> LandingSheetContent(p)
                        PreviewScreen.SETUP -> SetupSheetContent(
                            palette = p,
                            mode = mode,
                            motion = motion,
                            selectedBusIdx = selectedBusIdx,
                            onSelectMode = {
                                mode = it
                                activeStageIdx = 0
                            },
                            onSelectBus = { selectedBusIdx = it },
                            onStart = {
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
                            onEnd = { screen = PreviewScreen.LANDING }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // 4. Saved Places & History Overlay Sheet
        if (modalKind != ModalKind.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clickable { modalKind = ModalKind.NONE }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
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
                                    mode = item.mode
                                    activeStageIdx = 0
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
    animSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>
) {
    val mapScale by animateFloatAsState(
        targetValue = when (screen) {
            PreviewScreen.LANDING -> 1f
            PreviewScreen.SETUP -> 1.06f
            PreviewScreen.ACTIVE -> 1.14f
        },
        animationSpec = animSpec,
        label = "mapScale"
    )
    val mapRotation by animateFloatAsState(
        targetValue = if (screen == PreviewScreen.ACTIVE) -10f else 0f,
        animationSpec = animSpec,
        label = "mapRot"
    )
    val mapShiftY by animateFloatAsState(
        targetValue = when (screen) {
            PreviewScreen.LANDING -> 0f
            PreviewScreen.SETUP -> -80f
            PreviewScreen.ACTIVE -> -110f
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

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "pulseR"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = mapScale
                scaleY = mapScale
                rotationZ = mapRotation
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

        val showMetro = mode == TransitMode.METRO
        val metroAlpha = if (showMetro) 1f else 0.18f

        // Purple Line: Singayyanapalya -> Majestic
        val purplePath = Path().apply {
            moveTo(px(0.76f), py(0.42f))
            lineTo(px(0.52f), py(0.42f))
            lineTo(px(0.41f), py(0.38f))
        }
        drawPath(purplePath, palette.purple.copy(alpha = metroAlpha), style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Green Line: Majestic -> Madavara
        val greenPath = Path().apply {
            moveTo(px(0.41f), py(0.38f))
            lineTo(px(0.30f), py(0.28f))
            lineTo(px(0.21f), py(0.19f))
        }
        drawPath(greenPath, palette.green.copy(alpha = metroAlpha), style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (showMetro) {
            drawLine(
                palette.orange,
                Offset(px(0.84f), py(0.44f)),
                Offset(px(0.76f), py(0.42f)),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
            drawLine(
                palette.orange,
                Offset(px(0.21f), py(0.19f)),
                Offset(px(0.14f), py(0.14f)),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        } else {
            val roadPath = Path().apply {
                moveTo(px(0.84f), py(0.44f))
                lineTo(px(0.66f), py(0.34f))
                lineTo(px(0.42f), py(0.28f))
                lineTo(px(0.26f), py(0.21f))
                lineTo(px(0.14f), py(0.14f))
            }
            drawPath(
                roadPath,
                if (mode == TransitMode.BUS) palette.blue else palette.orange,
                style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Nodes
        drawCircle(Color.White, 11f, Offset(px(0.76f), py(0.42f)))
        drawCircle(palette.purple, 7f, Offset(px(0.76f), py(0.42f)))
        drawCircle(Color.White, 13f, Offset(px(0.41f), py(0.38f)))
        drawCircle(palette.blue, 8f, Offset(px(0.41f), py(0.38f)))
        drawCircle(Color.White, 11f, Offset(px(0.21f), py(0.19f)))
        drawCircle(palette.green, 7f, Offset(px(0.21f), py(0.19f)))
        drawCircle(Color.White, 13f, Offset(px(0.14f), py(0.14f)))
        drawCircle(palette.red, 9f, Offset(px(0.14f), py(0.14f)))

        // User Puck + Compass Heading Cone
        val userPos = Offset(px(0.84f), py(0.44f))
        rotate(coneAngle, pivot = userPos) {
            val conePath = Path().apply {
                moveTo(userPos.x, userPos.y)
                lineTo(userPos.x - 42f, userPos.y - 96f)
                lineTo(userPos.x + 42f, userPos.y - 96f)
                close()
            }
            drawPath(
                conePath,
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, palette.blue.copy(alpha = 0.34f)),
                    startY = userPos.y - 96f,
                    endY = userPos.y
                )
            )
        }
        if (motion == MotionPreset.ELASTIC) {
            drawCircle(palette.blue.copy(alpha = 0.25f), pulseRadius, userPos)
        }
        drawCircle(Color.White, 15f, userPos)
        drawCircle(palette.blue, 10f, userPos)
    }
}

@Composable
private fun LandingSheetContent(palette: WakeMePalette) {
    Column {
        Text("Near Phoenix Marketcity, Whitefield", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("Nearby Transit", color = palette.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(palette.purple))
            Column {
                Text("Singayyanapalya Metro • 466m", color = palette.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("BMTC: Singaianapalya Phoenix (236m, 132 routes)", color = palette.muted, fontSize = 12.sp)
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
    onSelectMode: (TransitMode) -> Unit,
    onSelectBus: (Int) -> Unit,
    onStart: () -> Unit
) {
    val busOpt = PreviewFixtures.busOptions[selectedBusIdx]
    val title = when (mode) {
        TransitMode.METRO -> "Acharya College • 27 Metro Stops"
        TransitMode.BUS -> "Acharya College • ${busOpt.title}"
        TransitMode.CAR -> "Acharya College • Direct Road"
    }

    Column {
        Text("Phoenix Marketcity → Acharya Institute of Technology", color = palette.muted, fontSize = 12.sp)
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
                        Column(modifier = Modifier.width(196.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.blue)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(opt.title, color = palette.badgeInk, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(opt.stops.substringAfter("= "), color = palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                TimelineRow(palette, palette.orange, "1. Reach Singayyanapalya Metro", "Switches to Purple Line tracking at 50m", "466m • 6m", true)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.purple, "2. Purple ➔ Green (via Majestic)", "12 stops to Majestic, 15 to Madavara", "27 stops", false)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.orange, "3. Madavara Metro ➔ Acharya College", "Final road leg after exit • Alerts at 150m", "3.3 km • 10m", true)
            }
            TransitMode.BUS -> {
                TimelineRow(palette, palette.orange, "1. Walk to Singaianapalya Phoenix", "132 routes pole • Switches at 50m", "236m • 3m", true)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.blue, "2. ${busOpt.title}", "${busOpt.via} • Alert 1 stop before ${busOpt.hub}", busOpt.stops, false)
                HorizontalDivider(color = palette.hairline)
                TimelineRow(palette, palette.orange, "3. Exit at Acharya Institute of Tech", "Final walk to campus gate • Alerts at 150m", "134m • 2m", true)
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
        TactileCtaButton(ctaText, palette.ctaBg, palette.ctaInk, pressScaleFor(motion), onStart)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(st.progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(st.accentColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        TimelineRow(palette, st.accentColor, st.nextTitle, st.nextSub, "", false)
        Spacer(Modifier.height(10.dp))
        TactileCtaButton("End Commute", palette.red, Color.White, pressScaleFor(motion), onEnd)
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
    pressScale: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = tween(120),
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
    val nextSub: String,
    val cueArrow: String,
    val cueMain: String,
    val cueSub: String
)

private fun activeStagesFor(mode: TransitMode, selectedBusIdx: Int, p: WakeMePalette): List<ActiveStageInfo> = when (mode) {
    TransitMode.METRO -> listOf(
        ActiveStageInfo("1. To Metro", true, "STAGE 1 • ROAD TO BOARDING", p.orange, "466 m", "~6 min walk", 0.18f, "Singayyanapalya Metro", "Auto-switches to Purple Line within 50m", "↗", "Head West 466m to Singayyanapalya", "ITPL Main Rd • Switches to ride at 50m"),
        ActiveStageInfo("2. Metro (27)", false, "PURPLE ➔ GREEN • VIA MAJESTIC", p.purple, "12 stops", "52 km/h • ~22 min", 0.48f, "Change at Majestic (12th stop)", "Interchange alert 1 stop before at Sir M. Visveshwaraya", "M", "Purple Line • Next: KR Pura", "Switch to Green Line (Madavara) at Majestic"),
        ActiveStageInfo("3. Final Road", true, "STAGE 3 • MADAVARA TO ACHARYA", p.orange, "3.3 km", "28 km/h • ~10 min", 0.86f, "Acharya Institute of Technology", "Gentle reminder fires at 150m along road", "↑", "Continue 3.3 km on Hesaraghatta Rd", "Final road leg • Alerts at 150m remaining")
    )
    TransitMode.BUS -> {
        val b = PreviewFixtures.busOptions[selectedBusIdx]
        listOf(
            ActiveStageInfo("1. To Stop", true, "STAGE 1 • ROAD TO BUS POLE", p.orange, "236 m", "~3 min walk", 0.15f, "Singaianapalya Phoenix (Westbound)", "Board ${b.leg1} • Auto-switches within 50m", "↗", "Walk 236m to Singaianapalya Phoenix", "Westbound pole toward KR Market / Majestic"),
            ActiveStageInfo("2. ${b.title}", false, "BMTC ${b.leg1} ➔ ${b.leg2} • 1 TRANSFER", p.blue, "${b.leg1Stops} stops", "24 km/h • ~54 min", 0.42f, "Transfer at ${b.hub} → ${b.leg2}", "Alert 1 stop before ${b.hub}, then ${b.leg2Stops} stops", "B", "Bus ${b.leg1} • ${b.leg1Stops} stops to ${b.hub}", "Then board ${b.leg2} to Acharya Institute"),
            ActiveStageInfo("3. Final Walk", true, "STAGE 3 • STOP TO CAMPUS", p.orange, "134 m", "~2 min walk", 0.95f, "Acharya Institute of Technology", "Within 150m final approach gate", "↑", "Walk 134m into Acharya Campus", "Arriving at destination")
        )
    }
    TransitMode.CAR -> listOf(
        ActiveStageInfo("Live Road Tracking", false, "DIRECT ROAD • CAB / AUTO", p.blue, "14.2 km", "33 km/h • ~26 min", 0.55f, "Approaching Hebbal / Tumkur Rd", "Gentle reminder armed at 1 km along road", "↑", "14.2 km remaining along road", "Gentle reminder triggers at 1 km")
    )
}

private fun directionCueFor(
    screen: PreviewScreen,
    mode: TransitMode,
    activeStageIdx: Int,
    selectedBusIdx: Int
): Triple<String, String, String> {
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
