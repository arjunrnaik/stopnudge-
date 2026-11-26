package com.wakeme.uipreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.atan2

@Composable
fun OsmMapCanvas(
    palette: WakeMePalette,
    isDark: Boolean,
    scenario: TripScenario,
    screen: PreviewScreen,
    mode: TransitMode,
    selectedBusIdx: Int,
    focusedLegStage: Int?,
    activeStageIdx: Int,
    activeTripProgress: Float
) {
    val context = LocalContext.current
    var currentZoom by remember { mutableDoubleStateOf(12.8) }
    var lastCameraKey by remember { mutableDoubleStateOf(0.0) }
    val cameraSignature = remember(scenario.id, screen, mode, selectedBusIdx, focusedLegStage, activeStageIdx) {
        "${scenario.id}|$screen|$mode|$selectedBusIdx|$focusedLegStage|$activeStageIdx".hashCode().toDouble()
    }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.8)
            controller.setCenter(scenario.originPoint)
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    currentZoom = zoomLevelDouble
                    return false
                }
            })
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { mv ->
            // Dark mode tile inversion so OSM tiles blend cleanly with dark themes
            mv.overlayManager.tilesOverlay.setColorFilter(
                if (isDark) TilesOverlay.INVERT_COLORS else null
            )

            mv.overlays.clear()

            val effectiveFocus = if (screen == PreviewScreen.ACTIVE && mode != TransitMode.CAR) activeStageIdx else focusedLegStage
            val showStage1 = effectiveFocus == null || effectiveFocus == 0
            val showStage2 = effectiveFocus == null || effectiveFocus == 1
            val showStage3 = effectiveFocus == null || effectiveFocus == 2

            val busOpt = scenario.busOptions[selectedBusIdx.coerceIn(0, scenario.busOptions.lastIndex)]
            val showDots = currentZoom >= 12.2
            val showNames = currentZoom >= 13.6
            val focusGeoPoints = mutableListOf<GeoPoint>()

            if (screen == PreviewScreen.LANDING) {
                // Page 1: clean normal road route only
                val roadLine = Polyline(mv).apply {
                    setPoints(scenario.carPlan.roadPoints)
                    outlinePaint.color = palette.blue.toArgb()
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                mv.overlays.add(roadLine)
                focusGeoPoints.addAll(scenario.carPlan.roadPoints)
            } else {
                when (mode) {
                    TransitMode.METRO -> {
                        val metro = scenario.metroPlan
                        if (metro != null) {
                            if (showStage1) {
                                val walkPts = listOf(scenario.originPoint, metro.boardStation.geoPoint)
                                mv.overlays.add(Polyline(mv).apply {
                                    setPoints(walkPts)
                                    outlinePaint.color = palette.orange.toArgb()
                                    outlinePaint.strokeWidth = 9f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                })
                                val arrowT = if (screen == PreviewScreen.ACTIVE && activeStageIdx == 0) activeTripProgress else 0.5f
                                val arrowPt = interpolateGeo(walkPts, arrowT)
                                mv.overlays.add(Marker(mv).apply {
                                    position = arrowPt
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = arrowDrawable(context, scenario.originPoint, metro.boardStation.geoPoint, palette.orange.toArgb())
                                })
                                focusGeoPoints.addAll(walkPts)
                            }
                            if (showStage2) {
                                metro.legs.forEach { leg ->
                                    val legPts = leg.stops.map { it.geoPoint }
                                    mv.overlays.add(Polyline(mv).apply {
                                        setPoints(legPts)
                                        outlinePaint.color = colorForMetroLine(leg.lineColor, palette)
                                        outlinePaint.strokeWidth = 12f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                    })
                                    focusGeoPoints.addAll(legPts)
                                }
                                if (showDots) {
                                    metro.allStops.forEachIndexed { idx, st ->
                                        val isKey = idx == 0 || idx == metro.allStops.lastIndex || st.line == MetroLineColor.INTERCHANGE
                                        val dotColor = colorForMetroLine(st.line, palette)
                                        mv.overlays.add(Marker(mv).apply {
                                            position = st.geoPoint
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                            icon = stopDotDrawable(context, dotColor, if (showNames && (isKey || currentZoom >= 14.3)) st.name else null, palette.ink.toArgb())
                                        })
                                    }
                                }
                            }
                            if (showStage3) {
                                val exitPts = listOf(metro.exitStation.geoPoint, scenario.destPoint)
                                mv.overlays.add(Polyline(mv).apply {
                                    setPoints(exitPts)
                                    outlinePaint.color = palette.orange.toArgb()
                                    outlinePaint.strokeWidth = 9f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                })
                                val arrowT = if (screen == PreviewScreen.ACTIVE && activeStageIdx == 2) activeTripProgress else 0.5f
                                mv.overlays.add(Marker(mv).apply {
                                    position = interpolateGeo(exitPts, arrowT)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = arrowDrawable(context, metro.exitStation.geoPoint, scenario.destPoint, palette.orange.toArgb())
                                })
                                focusGeoPoints.addAll(exitPts)
                            }
                            if (screen == PreviewScreen.ACTIVE && activeStageIdx == 1) {
                                val allPts = metro.allStops.map { it.geoPoint }
                                val vehPos = interpolateGeo(allPts, activeTripProgress)
                                mv.overlays.add(Marker(mv).apply {
                                    position = vehPos
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = vehicleHeadDrawable(context, TransitMode.METRO, palette.purple.toArgb())
                                })
                            }
                        } else {
                            // No direct metro in this scenario: show road fallback
                            mv.overlays.add(Polyline(mv).apply {
                                setPoints(scenario.carPlan.roadPoints)
                                outlinePaint.color = palette.muted.toArgb()
                                outlinePaint.strokeWidth = 8f
                            })
                            focusGeoPoints.addAll(scenario.carPlan.roadPoints)
                        }
                    }

                    TransitMode.BUS -> {
                        val firstStop = busOpt.stops.first().geoPoint
                        val lastStop = busOpt.stops.last().geoPoint
                        if (showStage1) {
                            val walkPts = listOf(scenario.originPoint, firstStop)
                            mv.overlays.add(Polyline(mv).apply {
                                setPoints(walkPts)
                                outlinePaint.color = palette.orange.toArgb()
                                outlinePaint.strokeWidth = 9f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                            })
                            val arrowT = if (screen == PreviewScreen.ACTIVE && activeStageIdx == 0) activeTripProgress else 0.5f
                            mv.overlays.add(Marker(mv).apply {
                                position = interpolateGeo(walkPts, arrowT)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = arrowDrawable(context, scenario.originPoint, firstStop, palette.orange.toArgb())
                            })
                            focusGeoPoints.addAll(walkPts)
                        }
                        if (showStage2) {
                            val busPts = busOpt.stops.map { it.geoPoint }
                            mv.overlays.add(Polyline(mv).apply {
                                setPoints(busPts)
                                outlinePaint.color = palette.blue.toArgb()
                                outlinePaint.strokeWidth = 11f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                            })
                            focusGeoPoints.addAll(busPts)
                            if (showDots) {
                                busOpt.stops.forEachIndexed { idx, st ->
                                    val isKey = idx == 0 || idx == busOpt.stops.lastIndex || st.name.contains("Transfer", true)
                                    mv.overlays.add(Marker(mv).apply {
                                        position = st.geoPoint
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        icon = stopDotDrawable(context, palette.blue.toArgb(), if (showNames && (isKey || currentZoom >= 14.3)) st.name else null, palette.ink.toArgb())
                                    })
                                }
                            }
                        }
                        if (showStage3) {
                            val exitPts = listOf(lastStop, scenario.destPoint)
                            mv.overlays.add(Polyline(mv).apply {
                                setPoints(exitPts)
                                outlinePaint.color = palette.orange.toArgb()
                                outlinePaint.strokeWidth = 9f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                            })
                            val arrowT = if (screen == PreviewScreen.ACTIVE && activeStageIdx == 2) activeTripProgress else 0.5f
                            mv.overlays.add(Marker(mv).apply {
                                position = interpolateGeo(exitPts, arrowT)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = arrowDrawable(context, lastStop, scenario.destPoint, palette.orange.toArgb())
                            })
                            focusGeoPoints.addAll(exitPts)
                        }
                        if (screen == PreviewScreen.ACTIVE && activeStageIdx == 1) {
                            val busPts = busOpt.stops.map { it.geoPoint }
                            mv.overlays.add(Marker(mv).apply {
                                position = interpolateGeo(busPts, activeTripProgress)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = vehicleHeadDrawable(context, TransitMode.BUS, palette.blue.toArgb())
                            })
                        }
                    }

                    TransitMode.CAR -> {
                        mv.overlays.add(Polyline(mv).apply {
                            setPoints(scenario.carPlan.roadPoints)
                            outlinePaint.color = palette.orange.toArgb()
                            outlinePaint.strokeWidth = 11f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                        })
                        focusGeoPoints.addAll(scenario.carPlan.roadPoints)
                        if (screen == PreviewScreen.ACTIVE) {
                            mv.overlays.add(Marker(mv).apply {
                                position = interpolateGeo(scenario.carPlan.roadPoints, activeTripProgress)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = vehicleHeadDrawable(context, TransitMode.CAR, palette.orange.toArgb())
                            })
                        }
                    }
                }
            }

            // Origin User Puck & Destination Pin
            if (showStage1 || screen == PreviewScreen.LANDING) {
                mv.overlays.add(Marker(mv).apply {
                    position = scenario.originPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = originPuckDrawable(context, palette.blue.toArgb())
                })
            }
            if (showStage3 || screen == PreviewScreen.LANDING || mode == TransitMode.CAR) {
                mv.overlays.add(Marker(mv).apply {
                    position = scenario.destPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = stopDotDrawable(context, palette.red.toArgb(), if (showNames) scenario.destName else null, palette.ink.toArgb(), radiusPx = 15f)
                })
            }

            if (cameraSignature != lastCameraKey && focusGeoPoints.isNotEmpty()) {
                lastCameraKey = cameraSignature
                mv.post {
                    runCatching {
                        val box = BoundingBox.fromGeoPoints(focusGeoPoints)
                        // Pad slightly wider vertically so points sit cleanly above the bottom sheet
                        val latSpan = (box.latNorth - box.latSouth).coerceAtLeast(0.004)
                        val lonSpan = (box.lonEast - box.lonWest).coerceAtLeast(0.004)
                        val padded = BoundingBox(
                            box.latNorth + latSpan * 0.25,
                            box.lonEast + lonSpan * 0.18,
                            box.latSouth - latSpan * 0.95,
                            box.lonWest - lonSpan * 0.18
                        )
                        mv.zoomToBoundingBox(padded, true, 64)
                    }
                }
            }

            mv.invalidate()
        }
    )
}

private fun colorForMetroLine(line: MetroLineColor, p: WakeMePalette): Int = when (line) {
    MetroLineColor.PURPLE -> p.purple.toArgb()
    MetroLineColor.GREEN -> p.green.toArgb()
    MetroLineColor.YELLOW -> android.graphics.Color.parseColor("#CA8A04")
    MetroLineColor.PINK -> android.graphics.Color.parseColor("#DB2777")
    MetroLineColor.INTERCHANGE -> p.blue.toArgb()
    MetroLineColor.BUS -> p.blue.toArgb()
}

private fun interpolateGeo(pts: List<GeoPoint>, t: Float): GeoPoint {
    if (pts.isEmpty()) return GeoPoint(12.9716, 77.5946)
    if (pts.size == 1) return pts.first()
    val segs = pts.size - 1
    val scaled = t.coerceIn(0f, 0.9999f) * segs
    val idx = scaled.toInt().coerceIn(0, segs - 1)
    val f = scaled - idx
    val a = pts[idx]
    val b = pts[idx + 1]
    return GeoPoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)
}

private fun originPuckDrawable(context: Context, blueColor: Int): BitmapDrawable {
    val size = 56
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.argb(60, android.graphics.Color.red(blueColor), android.graphics.Color.green(blueColor), android.graphics.Color.blue(blueColor))
    c.drawCircle(size / 2f, size / 2f, 24f, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(size / 2f, size / 2f, 14f, p)
    p.color = blueColor
    c.drawCircle(size / 2f, size / 2f, 9.5f, p)
    return BitmapDrawable(context.resources, bmp)
}

private fun stopDotDrawable(context: Context, fillColor: Int, label: String?, textColor: Int, radiusPx: Float = 11f): BitmapDrawable {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textW = if (label != null) textPaint.measureText(label).toInt() + 24 else 0
    val w = (radiusPx * 2 + 12 + textW).toInt().coerceAtLeast(32)
    val h = 40
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = radiusPx + 4f
    val cy = h / 2f
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, radiusPx, p)
    p.color = fillColor
    c.drawCircle(cx, cy, radiusPx * 0.68f, p)
    if (label != null) {
        c.drawText(label, cx + radiusPx + 6f, cy + 9f, textPaint)
    }
    return BitmapDrawable(context.resources, bmp)
}

private fun arrowDrawable(context: Context, start: GeoPoint, end: GeoPoint, color: Int): BitmapDrawable {
    val size = 44
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val angle = Math.toDegrees(atan2(-(end.latitude - start.latitude), end.longitude - start.longitude)).toFloat() + 90f
    c.save()
    c.rotate(angle, size / 2f, size / 2f)
    val path = AndroidPath().apply {
        moveTo(size / 2f, 6f)
        lineTo(size / 2f - 12f, size - 8f)
        lineTo(size / 2f + 12f, size - 8f)
        close()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    c.drawPath(path, fill)
    c.drawPath(path, stroke)
    c.restore()
    return BitmapDrawable(context.resources, bmp)
}

private fun vehicleHeadDrawable(context: Context, mode: TransitMode, accentColor: Int): BitmapDrawable {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 24f, p)
    p.color = accentColor
    p.style = Paint.Style.STROKE
    p.strokeWidth = 4.5f
    c.drawCircle(cx, cy, 24f, p)
    p.style = Paint.Style.FILL

    val dark = android.graphics.Color.parseColor("#0F172A")
    when (mode) {
        TransitMode.METRO -> {
            p.color = dark
            c.drawRoundRect(RectF(cx - 10f, cy - 14f, cx + 10f, cy + 12f), 8f, 8f, p)
            p.color = accentColor
            p.strokeWidth = 3f
            c.drawLine(cx - 6f, cy - 16f, cx + 6f, cy - 16f, p)
            p.color = android.graphics.Color.parseColor("#38BDF8")
            c.drawRoundRect(RectF(cx - 7f, cy - 9f, cx + 7f, cy - 1f), 3f, 3f, p)
            p.color = android.graphics.Color.parseColor("#FACC15")
            c.drawCircle(cx - 5f, cy + 6f, 2.5f, p)
            c.drawCircle(cx + 5f, cy + 6f, 2.5f, p)
        }
        TransitMode.BUS -> {
            p.color = dark
            c.drawRoundRect(RectF(cx - 11f, cy - 13f, cx + 11f, cy + 12f), 5f, 5f, p)
            p.color = accentColor
            c.drawRect(RectF(cx - 7f, cy - 10.5f, cx + 7f, cy - 7.5f), p)
            p.color = android.graphics.Color.parseColor("#93C5FD")
            c.drawRect(RectF(cx - 8f, cy - 5.5f, cx + 8f, cy + 3f), p)
            p.color = android.graphics.Color.parseColor("#FACC15")
            c.drawCircle(cx - 6f, cy + 7.5f, 2.3f, p)
            c.drawCircle(cx + 6f, cy + 7.5f, 2.3f, p)
        }
        TransitMode.CAR -> {
            p.color = dark
            c.drawRoundRect(RectF(cx - 12f, cy - 7f, cx + 12f, cy + 6f), 5f, 5f, p)
            c.drawCircle(cx - 6.5f, cy + 7.5f, 3.2f, p)
            c.drawCircle(cx + 6.5f, cy + 7.5f, 3.2f, p)
        }
    }
    return BitmapDrawable(context.resources, bmp)
}
