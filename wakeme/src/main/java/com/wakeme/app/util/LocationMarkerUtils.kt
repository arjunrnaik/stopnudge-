package com.wakeme.app.util

import android.content.Context
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

    fun createStationMarkerDrawable(
        context: Context,
        stationName: String,
        lineColor: Int,
        isInterchange: Boolean = false
    ): Drawable {
        val bmp = createStationMarkerBitmap(context, stationName, lineColor, isInterchange)
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
     * Generates a circular BMTC bus stop badge.
     */
    fun createBusStopBadgeBitmap(
        context: Context,
        sizeDp: Int = 22
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f

        val whiteHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawCircle(center, center, center * 0.95f, whiteHalo)

        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00897B")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.80f, busPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sizePx * 0.48f
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val textY = center - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText("B", center, textY, textPaint)

        return bitmap
    }

    fun createBusStopBadgeDrawable(context: Context, sizeDp: Int = 22): Drawable {
        val bmp = createBusStopBadgeBitmap(context, sizeDp)
        val drawable = BitmapDrawable(context.resources, bmp)
        drawable.setBounds(0, 0, bmp.width, bmp.height)
        return drawable
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
}
