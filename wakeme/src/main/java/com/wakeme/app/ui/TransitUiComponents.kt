package com.wakeme.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeme.app.data.BmtcRouteOption
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.MetroLeg
import com.wakeme.app.data.RouteResult
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.service.MetroJourneyStage
import java.util.Locale

private val OrangeAccent = Color(0xFFE65100)
private val OrangeBg = Color(0xFFFF6D00).copy(alpha = 0.13f)
private val SuccessGreen = Color(0xFF2E7D32)
private val SuccessGreenBg = Color(0xFF2E7D32).copy(alpha = 0.13f)
private val AlertCrimson = Color(0xFFD32F2F)

fun formatRoadDistanceAndMin(distMeters: Float, durationSec: Float?): Pair<String, String> {
    val d = if (distMeters >= 1000f) {
        String.format(Locale.US, "%.1f km", distMeters / 1000f)
    } else {
        "${distMeters.toInt()}m"
    }
    val mins = ((durationSec ?: (distMeters / 8.33f)) / 60).toInt().coerceAtLeast(1)
    return d to "~$mins min"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransitRoutePillRow(
    routesCsv: String?,
    maxVisible: Int = 8,
    modifier: Modifier = Modifier
) {
    val tokens = remember(routesCsv) {
        routesCsv.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    if (tokens.isEmpty()) return

    val visible = tokens.take(maxVisible)
    val overflow = tokens.size - visible.size

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        visible.forEach { routeNo ->
            val catColor = BmtcBusCategory.fromRouteOrName(routeNo).composeColor
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = catColor.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, catColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = routeNo,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = catColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (overflow > 0) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "+$overflow more",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun BmtcMapLegendBanner(
    zoomLevel: Double,
    stopCount: Int,
    modifier: Modifier = Modifier
) {
    val isZoomedOut = zoomLevel < 14.5
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBus,
                contentDescription = null,
                tint = Color(0xFF0D47A1),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isZoomedOut) {
                    "Showing $stopCount major hubs • Zoom in for street stops"
                } else {
                    "Showing street stops on both road sides"
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0D47A1)
            )
        }
    }
}

@Composable
fun BmtcRouteOptionCards(
    options: List<BmtcRouteOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Suggested BMTC Routes (${options.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { idx, opt ->
                val isSelected = idx == selectedIndex
                val accentColor = opt.category.composeColor
                Surface(
                    onClick = { onSelect(idx) },
                    modifier = Modifier.width(232.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) accentColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (opt.isTransferRoute) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Text(
                                            text = opt.routeTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = accentColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (opt.isTransferRoute) "1 Transfer" else opt.category.shortCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "${opt.plan.boardingStation.name} → ${opt.plan.exitStation.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (opt.frequencyText.isNotBlank()) {
                            Text(
                                text = opt.frequencyText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = opt.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransitThreeStageStepperCard(
    plan: MetroJourneyPlan,
    boardingRoadRoute: RouteResult?,
    exitRoadRoute: RouteResult?,
    stopsBeforeAlert: Int,
    isCustomStart: Boolean,
    isLoadingRoutes: Boolean,
    onOpenStartPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isStopsExpanded by remember(plan) { mutableStateOf(false) }
    val totalUniqueStops = remember(plan) {
        plan.legs.sumOf { it.stopsCount } + 1
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stage 1: Reach Boarding Stop / Station (Orange)
        val stage1Title = "Reach ${plan.boardingStation.name} ${if (!isCustomStart) "(Nearest)" else "(Custom)"}"
        val (s1BadgeText, s1BadgeColor, s1BadgeBg) = when {
            isLoadingRoutes && boardingRoadRoute == null -> Triple("Calculating...", OrangeAccent, OrangeBg)
            boardingRoadRoute != null -> {
                val (d, t) = formatRoadDistanceAndMin(boardingRoadRoute.distanceMeters, boardingRoadRoute.durationSeconds)
                Triple("$d ($t)", OrangeAccent, OrangeBg)
            }
            else -> Triple("Within 50m", SuccessGreen, SuccessGreenBg)
        }

        StageStepperRow(
            stepNumber = "1",
            stepColor = OrangeAccent,
            title = stage1Title,
            subtitle = if (boardingRoadRoute != null) {
                "Road path to boarding ${if (plan.isBusJourney) "stop" else "station"} • Switches at 50m"
            } else {
                "Already at boarding ${if (plan.isBusJourney) "stop" else "station"}"
            },
            badgeText = s1BadgeText,
            badgeTextColor = s1BadgeColor,
            badgeBgColor = s1BadgeBg,
            trailingAction = {
                TextButton(
                    onClick = onOpenStartPicker,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Change Start ▾", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        // Stage 2: Transit Ride + Transfer & Alert Preview
        val primaryLeg = plan.legs.firstOrNull()
        val stage2Color = primaryLeg?.let { Color(it.lineColorInt) } ?: MaterialTheme.colorScheme.primary
        val legTitleText = if (plan.isBusJourney) {
            "Bus ${plan.legs.joinToString(" → ") { it.lineName }}"
        } else {
            plan.legs.joinToString(" → ") { "${it.lineName} Line" }
        }
        val lastLegIdx = (plan.legs.size - 1).coerceAtLeast(0)
        val finalAlertStop = plan.getAlertStopForLeg(lastLegIdx, stopsBeforeAlert)
        val stage2Sub = buildString {
            if (plan.requiresLineChange) {
                append("Change at ${plan.interchangeStations.joinToString { it.name }} • ")
            } else if (primaryLeg != null) {
                append("${primaryLeg.directionLabel} • ")
            }
            append("Alert at ${finalAlertStop?.name ?: plan.exitStation.name}")
        }

        StageStepperRow(
            stepNumber = "2",
            stepColor = stage2Color,
            title = legTitleText,
            subtitle = stage2Sub,
            badgeText = "${plan.totalStops} stop${if (plan.totalStops == 1) "" else "s"}",
            badgeTextColor = stage2Color,
            badgeBgColor = stage2Color.copy(alpha = 0.14f),
            trailingAction = {
                Surface(
                    onClick = { isStopsExpanded = !isStopsExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (isStopsExpanded) "Hide stops" else "View $totalUniqueStops stops",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stage2Color
                        )
                        Icon(
                            imageVector = if (isStopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = stage2Color,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        )

        // Expandable Stop-by-Stop Timeline
        AnimatedVisibility(visible = isStopsExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    plan.legs.forEachIndexed { legIdx, leg ->
                        val lColor = Color(leg.lineColorInt)
                        val alertStop = plan.getAlertStopForLeg(legIdx, stopsBeforeAlert)
                        val isLastLeg = legIdx == plan.legs.size - 1

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = lColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (leg.isBusLeg) {
                                    "Route ${leg.lineName} • ${leg.directionLabel}"
                                } else {
                                    "${leg.lineName} Line • ${leg.directionLabel}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = lColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        leg.stations.forEachIndexed { stIdx, st ->
                            val isBoard = legIdx == 0 && stIdx == 0
                            val isTransferOut = !isLastLeg && stIdx == leg.stations.size - 1
                            val isTransferIn = legIdx > 0 && stIdx == 0
                            val isFinalExit = isLastLeg && stIdx == leg.stations.size - 1
                            val isAlert = alertStop?.id == st.id && stIdx == leg.stations.indexOfFirst { it.id == alertStop.id }

                            StopTimelineRow(
                                stopName = st.name,
                                railColor = lColor,
                                showTopLine = stIdx > 0,
                                showBottomLine = stIdx < leg.stations.size - 1,
                                tagLabel = when {
                                    isBoard && isAlert -> "BOARD • ALERT"
                                    isBoard -> "BOARD"
                                    isTransferOut -> "CHANGE"
                                    isTransferIn -> "NEXT LEG"
                                    isFinalExit && isAlert -> "EXIT • ALERT"
                                    isFinalExit -> "EXIT"
                                    isAlert -> "ALERT"
                                    else -> null
                                },
                                tagColor = when {
                                    isAlert -> AlertCrimson
                                    isTransferOut || isTransferIn -> OrangeAccent
                                    isBoard -> SuccessGreen
                                    else -> lColor
                                },
                                isHighlighted = isBoard || isTransferOut || isTransferIn || isFinalExit || isAlert
                            )
                        }
                    }
                }
            }
        }

        // Stage 3: Final Road Path to Destination (Orange if > 50m)
        if (plan.exitWalkDistanceMeters > 50f) {
            val ed = exitRoadRoute?.distanceMeters ?: (plan.exitWalkDistanceMeters * 1.28f)
            val (edText, eMinText) = formatRoadDistanceAndMin(ed, exitRoadRoute?.durationSeconds)
            StageStepperRow(
                stepNumber = "3",
                stepColor = OrangeAccent,
                title = "From ${plan.exitStation.name} → Destination",
                subtitle = "Road path after exit • Alerts at 150m",
                badgeText = "$edText ($eMinText)",
                badgeTextColor = OrangeAccent,
                badgeBgColor = OrangeBg
            )
        } else {
            StageStepperRow(
                stepNumber = "3",
                stepColor = SuccessGreen,
                title = "Destination within 50m of ${plan.exitStation.name}",
                subtitle = "No extra road leg needed after exit",
                badgeText = "≤ 50m",
                badgeTextColor = SuccessGreen,
                badgeBgColor = SuccessGreenBg
            )
        }
    }
}

@Composable
private fun StageStepperRow(
    stepNumber: String,
    stepColor: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeTextColor: Color,
    badgeBgColor: Color,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = stepColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                trailingAction?.invoke()
            }
        }
    }
}

@Composable
private fun StopTimelineRow(
    stopName: String,
    railColor: Color,
    showTopLine: Boolean,
    showBottomLine: Boolean,
    tagLabel: String?,
    tagColor: Color,
    isHighlighted: Boolean,
    isPassed: Boolean = false
) {
    val dotColor = when {
        isPassed -> SuccessGreen
        tagLabel?.contains("ALERT") == true -> AlertCrimson
        isHighlighted -> tagColor
        else -> railColor.copy(alpha = 0.50f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.width(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (showTopLine) railColor.copy(alpha = 0.45f) else Color.Transparent)
                )
                Box(
                    modifier = Modifier
                        .size(if (isHighlighted) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (showBottomLine) railColor.copy(alpha = 0.45f) else Color.Transparent)
                )
            }
        }

        if (isPassed) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(12.dp)
            )
        }

        Text(
            text = stopName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isPassed) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (tagLabel != null) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = tagColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = tagLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun LiveStageStepperPill(
    stage: MetroJourneyStage,
    isBus: Boolean,
    hasStage3: Boolean,
    modifier: Modifier = Modifier
) {
    val activeIdx = when (stage) {
        MetroJourneyStage.TO_BOARDING_ROAD, MetroJourneyStage.NONE -> 0
        MetroJourneyStage.ON_METRO_LEG, MetroJourneyStage.INTERCHANGE_WAIT -> 1
        MetroJourneyStage.TO_FINAL_DESTINATION_ROAD -> 2
    }
    val step1Label = if (isBus) "To Stop" else "To Metro"
    val step2Label = if (isBus) "On Bus" else "On Metro"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StageChip("1", step1Label, isDone = activeIdx > 0, isCurrent = activeIdx == 0, activeColor = OrangeAccent)
        Box(modifier = Modifier.width(12.dp).height(2.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)))
        StageChip("2", step2Label, isDone = activeIdx > 1, isCurrent = activeIdx == 1, activeColor = Color(0xFF1565C0))
        if (hasStage3) {
            Box(modifier = Modifier.width(12.dp).height(2.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)))
            StageChip("3", "Final Road", isDone = false, isCurrent = activeIdx == 2, activeColor = OrangeAccent)
        }
    }
}

@Composable
private fun StageChip(
    num: String,
    label: String,
    isDone: Boolean,
    isCurrent: Boolean,
    activeColor: Color
) {
    val bg = when {
        isDone -> SuccessGreen.copy(alpha = 0.18f)
        isCurrent -> activeColor.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    }
    val fg = when {
        isDone -> SuccessGreen
        isCurrent -> activeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = if (isCurrent) BorderStroke(1.dp, activeColor.copy(alpha = 0.55f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
            } else {
                Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
fun LiveUpcomingStopsTimeline(
    leg: MetroLeg,
    currentStationName: String?,
    nextStationName: String?,
    alertStationName: String?,
    modifier: Modifier = Modifier
) {
    var expanded by remember(leg) { mutableStateOf(false) }
    val currentIdx = remember(leg, currentStationName) {
        leg.stations.indexOfFirst { it.name.equals(currentStationName, ignoreCase = true) }.coerceAtLeast(0)
    }
    val lColor = Color(leg.lineColorInt)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Now: ${currentStationName ?: leg.boardingStation.name}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = lColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (expanded) "Hide stops ▴" else "All ${leg.stopsCount + 1} stops ▾",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!nextStationName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = lColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Next: $nextStationName",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!alertStationName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = AlertCrimson.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = AlertCrimson,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = alertStationName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertCrimson,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    leg.stations.forEachIndexed { idx, st ->
                        val isPassed = idx < currentIdx
                        val isCurrent = idx == currentIdx
                        val isAlert = alertStationName != null && st.name.equals(alertStationName, ignoreCase = true)
                        val isExit = idx == leg.stations.size - 1
                        StopTimelineRow(
                            stopName = st.name,
                            railColor = lColor,
                            showTopLine = idx > 0,
                            showBottomLine = idx < leg.stations.size - 1,
                            tagLabel = when {
                                isCurrent && isAlert -> "NOW • ALERT"
                                isCurrent -> "CURRENT"
                                isExit && isAlert -> "EXIT • ALERT"
                                isExit -> "EXIT"
                                isAlert -> "ALERT"
                                else -> null
                            },
                            tagColor = when {
                                isAlert -> AlertCrimson
                                isCurrent -> lColor
                                isExit -> SuccessGreen
                                else -> lColor
                            },
                            isHighlighted = isCurrent || isAlert || isExit,
                            isPassed = isPassed
                        )
                    }
                }
            }
        }
    }
}
