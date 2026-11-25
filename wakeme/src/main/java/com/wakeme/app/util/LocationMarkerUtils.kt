package com.wakeme.app.util

import android.content.Context
import android.graphics.DashPathEffect
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.RoadRoutingRepository
import com.wakeme.app.data.RouteCandidate
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.VehicleMode
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Utility to replace OsmDroid's legacy "man in yellow" icon and default "finger pointing"
 * markers with modern, sleek Google Maps / Uber style pins, glowing blue GPS dots,
 * and authentic Namma Metro station markings with station name badges.
 */
object LocationMarkerUtils {

    /**
     * Applies a modern medium-sized blue GPS dot and directional navigation arrow to MyLocationNewOverlay.
     */
    fun applyModernLocationOverlay(context: Context, overlay: MyLocationNewOverlay) {
        val dotBitmap = createMediumBlueDotBitmap(context, sizeDp = 26)
        val arrowBitmap = createDirectionalChevronBitmap(context, sizeDp = 30)

        overlay.setPersonIcon(dotBitmap)
        overlay.setDirectionArrow(dotBitmap, arrowBitmap)
        overlay.setPersonAnchor(0.5f, 0.5f)
        overlay.setDirectionAnchor(0.5f, 0.5f)
    }

    /**
     * Generates a modern teardrop destination pin (Google Maps / Uber style):
     * - Soft ground drop shadow below tip
     * - Crimson Red (#EA4335) teardrop pin body with crisp white highlight
     * - Crisp white inner circle with inner colored core dot
     */
    fun createModernPinBitmap(
        context: Context,
        sizeDp: Int = 38,
        pinColor: Int = Color.parseColor("#EA4335")
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthPx = (sizeDp * density).toInt().coerceAtLeast(1)
        val heightPx = (sizeDp * 1.35f * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val cx = w / 2f
        val padding = 3f * density
        val radius = (w - 2f * padding) / 2f
        val tipY = h - 4f * density

        // 1. Ground contact drop-shadow under the pin tip
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(cx - radius * 0.45f, h - 5f * density, cx + radius * 0.45f, h - 1f * density, shadowPaint)

        // 2. Teardrop pin outline and fill
        val headCenterY = radius + padding
        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(
                cx - radius * 0.9f, tipY - (tipY - headCenterY) * 0.45f,
                cx - radius, headCenterY + radius * 0.5f,
                cx - radius, headCenterY
            )
            arcTo(cx - radius, headCenterY - radius, cx + radius, headCenterY + radius, 180f, 180f, false)
            cubicTo(
                cx + radius, headCenterY + radius * 0.5f,
                cx + radius * 0.9f, tipY - (tipY - headCenterY) * 0.45f,
                cx, tipY
            )
            close()
        }

        // Solid Pin Body
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(pinPath, pinPaint)

        // Pin body border / subtle white highlight
        val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawPath(pinPath, pinBorderPaint)

        // 3. Crisp white center circle
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, headCenterY, radius * 0.42f, whitePaint)

        // 4. Center colored inner core
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, headCenterY, radius * 0.22f, innerDotPaint)

        return bitmap
    }

    fun createModernPinDrawable(
        context: Context,
        sizeDp: Int = 38,
        pinColor: Int = Color.parseColor("#EA4335")
    ): Drawable {
        val bmp = createModernPinBitmap(context, sizeDp, pinColor)
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    /**
     * Generates a complete Metro Station marking with node badge and station name pill label:
     * - Line-colored circular station badge with white 'M' or '⇄' interchange symbol
     * - High-contrast station name tag underneath, matching the metro map in the screenshot
     */
    fun createStationMarkerBitmap(
        context: Context,
        stationName: String,
        lineColor: Int,
        isInterchange: Boolean = false
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val badgeRadius = 13f * density
        val badgeDiameter = badgeRadius * 2f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(stationName)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPaddingH = 7f * density
        val pillPaddingV = 3f * density
        val pillWidth = textWidth + pillPaddingH * 2f
        val pillHeight = textHeight + pillPaddingV * 2f

        val totalWidth = maxOf(badgeDiameter + 8f * density, pillWidth + 8f * density).toInt()
        val gap = 2f * density
        val totalHeight = (badgeDiameter + gap + pillHeight + 6f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = totalWidth / 2f
        val badgeCenterY = badgeRadius + 2f * density

        // 1. Station Node Halo & Fill
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(3f * density, 0f, 1f * density, Color.argb(120, 0, 0, 0))
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius, haloPaint)

        val nodeColor = if (isInterchange) Color.parseColor("#0D47A1") else lineColor
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = nodeColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius * 0.80f, fillPaint)

        // Inner "M" symbol
        val mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeRadius * 1.05f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val mMetrics = mPaint.fontMetrics
        val mY = badgeCenterY - (mMetrics.descent + mMetrics.ascent) / 2f
        canvas.drawText(if (isInterchange) "⇄" else "M", centerX, mY, mPaint)

        // 2. Station Name Pill
        val pillTop = badgeDiameter + 2f * density + gap
        val pillLeft = centerX - pillWidth / 2f
        val pillRight = centerX + pillWidth / 2f
        val pillBottom = pillTop + pillHeight
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillBgPaint)

        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = nodeColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillStrokePaint)

        // Station text
        val textY = pillTop + pillPaddingV - fontMetrics.ascent
        canvas.drawText(stationName, centerX, textY, textPaint)

        return bitmap
    }

    private val stationBitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    fun createStationMarkerDrawable(
        context: Context,
        stationName: String,
        lineColor: Int,
        isInterchange: Boolean = false
    ): Drawable {
        val key = "$stationName|$lineColor|$isInterchange"
        val bmp = stationBitmapCache.getOrPut(key) {
            createStationMarkerBitmap(context, stationName, lineColor, isInterchange)
        }
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    /**
     * Generates a modern minimalist Google Maps-style BMTC bus stop marker badge:
     * - Compact rounded square badge (22dp standard, 28dp selected)
     * - Crisp white background with BMTC Deep Teal border and bus silhouette
     * - Perfectly centered on road curbs / service roads with zero visual collision
     */
    fun createGoogleMapsStyleBusMarkerDrawable(
        context: Context,
        isSelected: Boolean = false,
        tealColorHex: String = "#00838F"
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val sizeDp = if (isSelected) 28f else 22f
        val sizePx = (sizeDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cornerRadius = 5f * density
        val margin = 1.5f * density
        val rect = RectF(margin, margin, sizePx - margin, sizePx - margin)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor(tealColorHex) else Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2.5f * density, 0f, 1f * density, Color.argb(90, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.WHITE else Color.parseColor(tealColorHex)
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        // Bus silhouette
        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.WHITE else Color.parseColor(tealColorHex)
            style = Paint.Style.FILL
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val bw = 6.2f * density
        val bh = 7.2f * density
        val busBody = RectF(cx - bw, cy - bh, cx + bw, cy + bh - 1.5f * density)
        canvas.drawRoundRect(busBody, 1.8f * density, 1.8f * density, busPaint)

        // Windshield cutout
        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor(tealColorHex) else Color.WHITE
            style = Paint.Style.FILL
        }
        val winRect = RectF(cx - bw * 0.75f, cy - bh * 0.75f, cx + bw * 0.75f, cy - bh * 0.1f)
        canvas.drawRoundRect(winRect, 1f * density, 1f * density, cutoutPaint)

        // Wheels
        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.WHITE else Color.parseColor(tealColorHex)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx - bw * 0.55f, cy + bh - 0.5f * density, 1.2f * density, wheelPaint)
        canvas.drawCircle(cx + bw * 0.55f, cy + bh - 0.5f * density, 1.2f * density, wheelPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        return drawable
    }

    /**
     * Generates a prominent BMTC Bus Stop marking with node badge and station name pill label:
     * - Official BMTC Deep Teal (#00838F) circular node badge with white bus silhouette
     * - High-contrast station name tag underneath, matching the Namma Metro style
     * - Distinct color code with zero clash with Metro Purple, Green, Yellow, Pink, or Blue.
     */
    fun createBmtcBusStopMarkerBitmap(
        context: Context,
        stopName: String,
        tealColorHex: String = "#00838F"
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val badgeRadius = 12f * density
        val badgeDiameter = badgeRadius * 2f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(stopName)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPaddingH = 7f * density
        val pillPaddingV = 3f * density
        val pillWidth = textWidth + pillPaddingH * 2f
        val pillHeight = textHeight + pillPaddingV * 2f

        val totalWidth = maxOf(badgeDiameter + 8f * density, pillWidth + 8f * density).toInt()
        val gap = 2f * density
        val totalHeight = (badgeDiameter + gap + pillHeight + 6f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = totalWidth / 2f
        val badgeCenterY = badgeRadius + 2f * density

        val tealColor = Color.parseColor(tealColorHex)

        // 1. Bus Node Halo & Fill
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(3f * density, 0f, 1f * density, Color.argb(110, 0, 0, 0))
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius, haloPaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius * 0.82f, fillPaint)

        // 2. White Bus Silhouette inside node
        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val innerR = badgeRadius * 0.82f
        val busW = innerR * 1.1f
        val busH = innerR * 1.3f
        val busLeft = centerX - busW / 2f
        val busTop = badgeCenterY - busH / 2f - 0.5f * density
        val busRight = busLeft + busW
        val busBottom = busTop + busH

        // Main bus body (rounded rect)
        val bodyRect = RectF(busLeft, busTop, busRight, busBottom)
        val cornerR = 1.8f * density
        canvas.drawRoundRect(bodyRect, cornerR, cornerR, busPaint)

        // Windshield cutout (in teal color)
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            style = Paint.Style.FILL
        }
        val winMarginX = 1.2f * density
        val winTop = busTop + 1.8f * density
        val winH = busH * 0.32f
        val winRect = RectF(busLeft + winMarginX, winTop, busRight - winMarginX, winTop + winH)
        val winR = 0.8f * density
        canvas.drawRoundRect(winRect, winR, winR, cutPaint)

        // Two front headlights (in teal color)
        val hlRadius = 0.8f * density
        val hlY = busBottom - 2.5f * density
        canvas.drawCircle(busLeft + 2.0f * density, hlY, hlRadius, cutPaint)
        canvas.drawCircle(busRight - 2.0f * density, hlY, hlRadius, cutPaint)

        // Wheels below body
        val wheelW = 1.5f * density
        val wheelH = 1.0f * density
        canvas.drawRect(busLeft + 1.0f * density, busBottom, busLeft + 1.0f * density + wheelW, busBottom + wheelH, busPaint)
        canvas.drawRect(busRight - 1.0f * density - wheelW, busBottom, busRight - 1.0f * density, busBottom + wheelH, busPaint)

        // 3. Station Name Pill
        val pillTop = badgeDiameter + 2f * density + gap
        val pillLeft = centerX - pillWidth / 2f
        val pillRight = centerX + pillWidth / 2f
        val pillBottom = pillTop + pillHeight
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillBgPaint)

        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillStrokePaint)

        // Station name text
        val textY = pillTop + pillPaddingV - fontMetrics.ascent
        canvas.drawText(stopName, centerX, textY, textPaint)

        return bitmap
    }

    fun createBmtcBusStopMarkerDrawable(
        context: Context,
        stopName: String,
        tealColorHex: String = "#00838F"
    ): Drawable {
        val key = "bus|$stopName|$tealColorHex"
        val bmp = stationBitmapCache.getOrPut(key) {
            createBmtcBusStopMarkerBitmap(context, stopName, tealColorHex)
        }
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    fun createBmtcCompactStopDrawable(
        context: Context,
        stopName: String,
        directionHint: String = "",
        isSelected: Boolean = false
    ): Drawable {
        val shortDir = directionHint.substringAfter("Towards ", "").substringBefore(",").trim()
        val label = if (shortDir.isNotEmpty() && !stopName.contains(shortDir, ignoreCase = true)) {
            "$stopName ➔ ${shortDir.take(14)}"
        } else {
            stopName
        }
        val key = "bmtc_compact|$label|$isSelected"
        val bmp = stationBitmapCache.getOrPut(key) {
            createBmtcBusStopMarkerBitmap(
                context = context,
                stopName = label,
                tealColorHex = if (isSelected) "#0D47A1" else "#1565C0"
            )
        }
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    /**
     * Calculates anchor Y so that the station node circle aligns exactly with the track line.
     */
    fun getStationMarkerAnchorY(context: Context): Float {
        val density = context.resources.displayMetrics.density
        val badgeRadius = 13f * density
        val badgeDiameter = badgeRadius * 2f
        val textHeight = 10f * density * 1.2f
        val pillHeight = textHeight + 6f * density
        val totalHeight = badgeDiameter + 2f * density + pillHeight + 6f * density
        val badgeCenterY = badgeRadius + 2f * density
        return (badgeCenterY / totalHeight).coerceIn(0.25f, 0.40f)
    }

    /**
     * Generates an authentic 50/50 two-tone circular BMTC bus badge:
     * - Top half: Crisp White (#FFFFFF) with category code ("AC", "Sarige", "EV")
     * - Bottom half: Respective Blue (#0D47A1 Navy / #1976D2 Royal / #03A9F4 Sky)
     * - Perfectly matches BMTC's real-world livery and avoids confusion with Namma Metro Blue Line.
     */
    fun createTwoToneBusBadgeBitmap(
        context: Context,
        category: BmtcBusCategory = BmtcBusCategory.NORMAL_SARIGE,
        sizeDp: Int = 26
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        val radius = center * 0.90f

        // 1. Soft ground shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(90, 0, 0, 0))
        }
        canvas.drawCircle(center, center, radius, shadowPaint)

        // 2. Base Circle (White)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, whitePaint)

        // 3. Bottom Half Semicircle (Category Blue)
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.FILL
        }
        val circleRect = RectF(center - radius, center - radius, center + radius, center + radius)
        canvas.drawArc(circleRect, 0f, 180f, true, bluePaint)

        // 4. Outer border stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // 5. Divider line between top white and bottom blue
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawLine(center - radius, center, center + radius, center, dividerPaint)

        // 6. Top text ("Sarige" / "AC" / "EV")
        val topTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = radius * 0.54f
            textAlign = Paint.Align.CENTER
        }
        val topMetrics = topTextPaint.fontMetrics
        val topY = (center - radius * 0.45f) - (topMetrics.descent + topMetrics.ascent) / 2f
        canvas.drawText(category.shortCode, center, topY, topTextPaint)

        // 7. Bottom text ("BUS")
        val bottomTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = radius * 0.52f
            textAlign = Paint.Align.CENTER
        }
        val botMetrics = bottomTextPaint.fontMetrics
        val botY = (center + radius * 0.48f) - (botMetrics.descent + botMetrics.ascent) / 2f
        canvas.drawText("BUS", center, botY, bottomTextPaint)

        return bitmap
    }

    fun createTwoToneBusBadgeDrawable(
        context: Context,
        category: BmtcBusCategory = BmtcBusCategory.NORMAL_SARIGE,
        sizeDp: Int = 26
    ): Drawable {
        val bmp = createTwoToneBusBadgeBitmap(context, category, sizeDp)
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    /**
     * Generates a complete BMTC Bus Hub / Stop marking with 50/50 two-tone badge
     * and high-contrast stop name pill tag underneath.
     */
    fun createTwoToneBusStopMarkerBitmap(
        context: Context,
        stopName: String,
        category: BmtcBusCategory = BmtcBusCategory.NORMAL_SARIGE
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val badgeRadius = 14f * density
        val badgeDiameter = badgeRadius * 2f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(stopName)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPaddingH = 7f * density
        val pillPaddingV = 3f * density
        val pillWidth = textWidth + pillPaddingH * 2f
        val pillHeight = textHeight + pillPaddingV * 2f

        val totalWidth = maxOf(badgeDiameter + 8f * density, pillWidth + 8f * density).toInt()
        val gap = 2f * density
        val totalHeight = (badgeDiameter + gap + pillHeight + 6f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = totalWidth / 2f
        val badgeCenterY = badgeRadius + 2f * density

        // Draw the 50/50 circular badge
        val circleRect = RectF(centerX - badgeRadius, badgeCenterY - badgeRadius, centerX + badgeRadius, badgeCenterY + badgeRadius)

        // Top half: Crisp White
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2.5f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius, whitePaint)

        // Bottom half: Category Blue
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.FILL
        }
        canvas.drawArc(circleRect, 0f, 180f, true, bluePaint)

        // Outer border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(centerX, badgeCenterY, badgeRadius, borderPaint)

        // Divider
        canvas.drawLine(centerX - badgeRadius, badgeCenterY, centerX + badgeRadius, badgeCenterY, borderPaint)

        // Top text: Category ShortCode
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            textSize = badgeRadius * 0.62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val topMetrics = topPaint.fontMetrics
        val topY = (badgeCenterY - badgeRadius * 0.45f) - (topMetrics.descent + topMetrics.ascent) / 2f
        canvas.drawText(category.shortCode, centerX, topY, topPaint)

        // Bottom text: "BUS"
        val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeRadius * 0.58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val botMetrics = botPaint.fontMetrics
        val botY = (badgeCenterY + badgeRadius * 0.48f) - (botMetrics.descent + botMetrics.ascent) / 2f
        canvas.drawText("BUS", centerX, botY, botPaint)

        // Station/Stop Name Pill underneath
        val pillTop = badgeDiameter + 2f * density + gap
        val pillLeft = centerX - pillWidth / 2f
        val pillRight = centerX + pillWidth / 2f
        val pillBottom = pillTop + pillHeight
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(70, 0, 0, 0))
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillBgPaint)

        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = category.primaryColorInt
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawRoundRect(pillRect, 5f * density, 5f * density, pillStrokePaint)

        val textY = pillTop + pillPaddingV - fontMetrics.ascent
        canvas.drawText(stopName, centerX, textY, textPaint)

        return bitmap
    }

    fun createTwoToneBusStopMarkerDrawable(
        context: Context,
        stopName: String,
        category: BmtcBusCategory = BmtcBusCategory.NORMAL_SARIGE
    ): Drawable {
        val bmp = createTwoToneBusStopMarkerBitmap(context, stopName, category)
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
    }

    /**
     * Creates a pair of OsmDroid polylines representing an authentic 50/50 two-tone (Blue + White) bus path:
     * - Base layer: Solid Pure White line.
     * - Top layer: Alternating dashed line in the category's blue shade (#0D47A1, #1976D2, #03A9F4).
     * Where the dash is drawn, it is Blue; where the gap is, the base White shows through.
     * Result: 50% Blue and 50% White striped road bus line!
     */
    fun createTwoToneBusPolylines(
        mapView: MapView,
        points: List<GeoPoint>,
        category: BmtcBusCategory,
        widthDp: Float = 7f
    ): Pair<Polyline, Polyline> {
        val density = mapView.context.resources.displayMetrics.density
        val strokePx = widthDp * density

        // 1. Base Layer: Solid Pure White line (casing)
        val baseWhiteLine = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = Color.WHITE
            outlinePaint.strokeWidth = strokePx + (2f * density)
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }

        // 2. Overlay Layer: 50/50 Alternating Blue Dashes
        val dashPx = 20f * density
        val dashedBlueLine = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = category.primaryColorInt
            outlinePaint.strokeWidth = strokePx
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(dashPx, dashPx), 0f)
            outlinePaint.isAntiAlias = true
        }

        return Pair(baseWhiteLine, dashedBlueLine)
    }

    /**
     * Generates a circular BMTC bus stop badge.
     */

    private var cachedGmapsBusDrawable: Drawable? = null

    /**
     * Generates a sleek, minimalist Google Maps-style bus stop marker.
     * Clean transit slate/dark blue-grey circle with a crisp white bus silhouette,
     * designed to sit cleanly on either side of the road at true boarding coordinates.
     */
    fun createGmapsBusStopMarkerBitmap(
        context: Context,
        sizeDp: Int = 20
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        val radius = center - 1.5f * density

        // 1. Subtle drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 0, 0, 0)
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(60, 0, 0, 0))
        }
        canvas.drawCircle(center, center, radius, shadowPaint)

        // 2. Google Maps Transit Blue-Grey / Slate Background (#3C5268)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3C5268")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        // 3. Subtle semi-transparent white border ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawCircle(center, center, radius, ringPaint)

        // 4. White Bus Silhouette
        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val busW = radius * 1.05f
        val busH = radius * 1.25f
        val busLeft = center - busW / 2f
        val busTop = center - busH / 2f - 0.5f * density
        val busRight = busLeft + busW
        val busBottom = busTop + busH

        // Main bus body (rounded rect)
        val bodyRect = RectF(busLeft, busTop, busRight, busBottom)
        val cornerR = 2.0f * density
        canvas.drawRoundRect(bodyRect, cornerR, cornerR, busPaint)

        // Windshield cutout (in background slate color)
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3C5268")
            style = Paint.Style.FILL
        }
        val winMarginX = 1.5f * density
        val winTop = busTop + 2.0f * density
        val winH = busH * 0.32f
        val winRect = RectF(busLeft + winMarginX, winTop, busRight - winMarginX, winTop + winH)
        val winR = 1.0f * density
        canvas.drawRoundRect(winRect, winR, winR, cutPaint)

        // Two front headlights (in cutPaint slate color)
        val hlRadius = 0.9f * density
        val hlY = busBottom - 2.8f * density
        canvas.drawCircle(busLeft + 2.4f * density, hlY, hlRadius, cutPaint)
        canvas.drawCircle(busRight - 2.4f * density, hlY, hlRadius, cutPaint)

        // Front bumper line (small subtle cutout)
        val bumperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3C5268")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f * density
        }
        canvas.drawLine(busLeft + 4.2f * density, hlY, busRight - 4.2f * density, hlY, bumperPaint)

        // Wheels / tires below body
        val wheelW = 1.6f * density
        val wheelH = 1.2f * density
        canvas.drawRect(busLeft + 1.2f * density, busBottom, busLeft + 1.2f * density + wheelW, busBottom + wheelH, busPaint)
        canvas.drawRect(busRight - 1.2f * density - wheelW, busBottom, busRight - 1.2f * density, busBottom + wheelH, busPaint)

        return bitmap
    }

    fun createGmapsBusStopMarkerDrawable(context: Context, sizeDp: Int = 20): Drawable {
        cachedGmapsBusDrawable?.let { return it.constantState?.newDrawable()?.mutate() ?: it }
        val bmp = createGmapsBusStopMarkerBitmap(context, sizeDp)
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        cachedGmapsBusDrawable = drawable
        return drawable
    }

    fun createBusStopBadgeBitmap(context: Context, sizeDp: Int = 20): Bitmap {
        return createGmapsBusStopMarkerBitmap(context, sizeDp)
    }

    fun createBusStopBadgeDrawable(context: Context, sizeDp: Int = 20): Drawable {
        return createGmapsBusStopMarkerDrawable(context, sizeDp)
    }

    /**
     * Generates a medium-sized Google Maps-style blue location dot.
     */
    fun createMediumBlueDotBitmap(context: Context, sizeDp: Int = 26): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 26, 115, 232)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.95f, glowPaint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(3f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawCircle(center, center, center * 0.65f, whitePaint)

        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A73E8")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.48f, bluePaint)

        val innerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.18f, innerGlow)

        return bitmap
    }

    /**
     * Generates a modern directional chevron navigation arrow.
     */
    fun createDirectionalChevronBitmap(context: Context, sizeDp: Int = 30): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = sizePx.toFloat()
        val h = sizePx.toFloat()

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.88f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.12f, h * 0.88f)
            close()
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, outlinePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A73E8")
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        return bitmap
    }

    /**
     * Renders mode-appropriate alert overlays cleanly without duplication:
     * - Road transport (Cab / Auto): Draws active road polyline + highlighted final alert road stretch (NO radius circle).
     * - Station/rail transport (Metro / Bus): Draws station radius geofence circle + optional route polylines.
     */
    const val COLOR_METRO_ROAD_ORANGE = 0xFFFF6D00.toInt()

    fun updateRoadAndAlertOverlays(
        map: MapView,
        vehicleMode: VehicleMode,
        targetLocation: GeoPoint,
        alertDistanceMeters: Float,
        primaryRoutePoints: List<GeoPoint>,
        alternativeRoutes: List<RouteCandidate> = emptyList(),
        themeColorInt: Int,
        targetName: String = "",
        primaryColorOverride: Int? = null
    ) {
        map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>())
        map.overlays.removeAll(map.overlays.filterIsInstance<Polyline>())

        for (alt in alternativeRoutes) {
            if (alt.points.size >= 2) {
                map.overlays.add(Polyline(map).apply {
                    setPoints(alt.points)
                    outlinePaint.color = 0xFF90A4AE.toInt()
                    outlinePaint.strokeWidth = 6f
                    infoWindow = null
                })
            }
        }

        if (primaryRoutePoints.size >= 2) {
            if (vehicleMode == VehicleMode.BUS) {
                val busCat = BmtcBusCategory.fromRouteOrName(targetName)
                val (baseWhite, dashedBlue) = createTwoToneBusPolylines(
                    mapView = map,
                    points = primaryRoutePoints,
                    category = busCat,
                    widthDp = 8.5f
                )
                map.overlays.add(baseWhite)
                map.overlays.add(dashedBlue)
            } else {
                val lineColor = primaryColorOverride
                    ?: if (vehicleMode == VehicleMode.METRO) NammaMetroNetwork.COLOR_PURPLE else 0xFF1976D2.toInt()
                map.overlays.add(Polyline(map).apply {
                    setPoints(primaryRoutePoints)
                    outlinePaint.color = lineColor
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    infoWindow = null
                })

                if (vehicleMode.usesRoadDistanceTrigger) {
                    val alertStretch = RoadRoutingRepository.extractFinalAlertSegment(primaryRoutePoints, alertDistanceMeters)
                    if (alertStretch.size >= 2) {
                        val stretchColor = if (primaryColorOverride != null) 0xFFD32F2F.toInt() else 0xFFFF6D00.toInt()
                        map.overlays.add(Polyline(map).apply {
                            setPoints(alertStretch)
                            outlinePaint.color = stretchColor
                            outlinePaint.strokeWidth = 12.5f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            infoWindow = null
                        })
                    }
                }
            }
        }

        if (!vehicleMode.usesRoadDistanceTrigger) {
            map.overlays.add(Polygon(map).apply {
                points = Polygon.pointsAsCircle(targetLocation, alertDistanceMeters.toDouble())
                fillPaint.color = (0x33000000 or (themeColorInt and 0x00FFFFFF))
                outlinePaint.color = themeColorInt
                outlinePaint.strokeWidth = 3.5f
            })
        }
    }

    /**
     * Renders the complete 3-stage Namma Metro journey on an OsmDroid MapView:
     * - Stage 1 (if far > 50m): Vibrant Orange road path to boarding station + 50m boarding ring
     * - Stage 2: Each Metro leg polyline in its authentic line color (Purple, Green, Yellow, Pink)
     * - Stage 3 (if exit > 50m from Point B): Vibrant Orange road path from exit station to Point B
     */
    fun renderMetroJourneyOverlays(
        map: MapView,
        context: Context,
        plan: MetroJourneyPlan,
        boardingRoadPoints: List<GeoPoint>,
        stopsBeforeAlert: Int,
        activeLegIndex: Int = -1,
        exitRoadPoints: List<GeoPoint> = emptyList(),
        busLegRoadPoints: Map<Int, List<GeoPoint>> = emptyMap()
    ): List<GeoPoint> {
        map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>())
        map.overlays.removeAll(map.overlays.filterIsInstance<Polyline>())
        val allPoints = mutableListOf<GeoPoint>()

        // 1. Stage 1 Road Path to Boarding Station / Stop (Orange)
        if (boardingRoadPoints.size >= 2) {
            map.overlays.add(Polyline(map).apply {
                setPoints(boardingRoadPoints)
                outlinePaint.color = COLOR_METRO_ROAD_ORANGE
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            })
            allPoints.addAll(boardingRoadPoints)
        }

        // 2. 50m Boarding Station / Stop Ring
        map.overlays.add(Polygon(map).apply {
            points = Polygon.pointsAsCircle(plan.boardingStation.geoPoint, 50.0)
            fillPaint.color = 0x26FF6D00
            outlinePaint.color = COLOR_METRO_ROAD_ORANGE
            outlinePaint.strokeWidth = 2.5f
        })

        // 3. Stage 2 Transit Legs (Metro rail or smooth BMTC road corridors)
        plan.legs.forEachIndexed { idx, leg ->
            val rawLegRoad = if (leg.isBusLeg) busLegRoadPoints[idx] else null
            val drawPoints = if (rawLegRoad != null && rawLegRoad.size >= 2) rawLegRoad else leg.trackPoints
            if (drawPoints.size >= 2) {
                val isCurrentOrPreview = activeLegIndex < 0 || idx >= activeLegIndex
                if (leg.isBusLeg && isCurrentOrPreview) {
                    val category = com.wakeme.app.data.local.transit.BmtcBusCategory.fromRouteOrName(leg.lineName)
                    val (baseLine, stripeLine) = createTwoToneBusPolylines(map, drawPoints, category, 9.5f)
                    map.overlays.add(baseLine)
                    map.overlays.add(stripeLine)
                } else {
                    map.overlays.add(Polyline(map).apply {
                        setPoints(drawPoints)
                        outlinePaint.color = if (isCurrentOrPreview) leg.lineColorInt else 0xFF90A4AE.toInt()
                        outlinePaint.strokeWidth = if (idx == activeLegIndex || activeLegIndex < 0) 10.5f else 6.5f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        infoWindow = null
                    })
                }
                allPoints.addAll(drawPoints)
            }

            // Highlight alert stop for this leg in Crimson Red so it stands out cleanly from Orange roads
            val alertStop = plan.getAlertStopForLeg(idx, stopsBeforeAlert)
            if (alertStop != null) {
                val alertRadius = if (idx == plan.legs.size - 1 && stopsBeforeAlert == 0) 50.0 else 250.0
                map.overlays.add(Polygon(map).apply {
                    points = Polygon.pointsAsCircle(alertStop.geoPoint, alertRadius)
                    fillPaint.color = 0x33D32F2F
                    outlinePaint.color = 0xFFD32F2F.toInt()
                    outlinePaint.strokeWidth = 3f
                })
            }
        }

        // 4. Stage 3 Real Road Path from Exit Metro Station to Final Destination (Point B, Orange)
        if (exitRoadPoints.size >= 2) {
            map.overlays.add(Polyline(map).apply {
                setPoints(exitRoadPoints)
                outlinePaint.color = COLOR_METRO_ROAD_ORANGE
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            })
            allPoints.addAll(exitRoadPoints)
        } else if (plan.exitWalkDistanceMeters > 50f) {
            val walkPts = listOf(plan.exitStation.geoPoint, plan.destinationPinPoint)
            map.overlays.add(Polyline(map).apply {
                setPoints(walkPts)
                outlinePaint.color = COLOR_METRO_ROAD_ORANGE
                outlinePaint.strokeWidth = 6f
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                infoWindow = null
            })
            allPoints.add(plan.destinationPinPoint)
        }

        return allPoints
    }
}
