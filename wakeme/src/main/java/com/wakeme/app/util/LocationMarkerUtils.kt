package com.wakeme.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Utility to replace OsmDroid's legacy "man in yellow" icon and default "finger pointing"
 * markers with modern, sleek Google Maps / Uber style pins, glowing blue GPS dots,
 * and authentic Namma Metro station badges.
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
            // Left flank to top circle
            cubicTo(
                cx - radius * 0.9f, tipY - (tipY - headCenterY) * 0.45f,
                cx - radius, headCenterY + radius * 0.5f,
                cx - radius, headCenterY
            )
            // Arc across top circle
            arcTo(cx - radius, headCenterY - radius, cx + radius, headCenterY + radius, 180f, 180f, false)
            // Right flank back to bottom tip
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
        return BitmapDrawable(context.resources, createModernPinBitmap(context, sizeDp, pinColor))
    }

    /**
     * Generates an authentic circular Namma Metro station badge:
     * - White outer border and subtle shadow
     * - Filled circle in the metro line color (Purple, Green, Yellow, Pink, Interchange)
     * - Bold white "M" symbol centered
     */
    fun createMetroStationBadgeBitmap(
        context: Context,
        lineColor: Int,
        sizeDp: Int = 26
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f

        // 1. Outer white halo / border
        val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(90, 0, 0, 0))
        }
        canvas.drawCircle(center, center, center * 0.95f, whiteHaloPaint)

        // 2. Line-colored solid circle
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.80f, linePaint)

        // 3. Crisp bold "M" letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sizePx * 0.48f
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val textY = center - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText("M", center, textY, textPaint)

        return bitmap
    }

    fun createMetroStationBadgeDrawable(
        context: Context,
        lineColor: Int,
        sizeDp: Int = 26
    ): Drawable {
        return BitmapDrawable(context.resources, createMetroStationBadgeBitmap(context, lineColor, sizeDp))
    }

    /**
     * Generates a circular BMTC bus stop badge:
     * - White outer halo
     * - BMTC Teal circle
     * - Bold white "B" letter
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

        // 1. Outer white halo
        val whiteHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawCircle(center, center, center * 0.95f, whiteHalo)

        // 2. BMTC Blue/Teal fill
        val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00897B")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.80f, busPaint)

        // 3. "B" letter
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
        return BitmapDrawable(context.resources, createBusStopBadgeBitmap(context, sizeDp))
    }

    /**
     * Generates a medium-sized Google Maps-style blue location dot:
     * - Outer soft semi-transparent blue pulse ring
     * - Crisp white separator circle
     * - Solid Google-blue (#1A73E8) core
     */
    fun createMediumBlueDotBitmap(context: Context, sizeDp: Int = 26): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f

        // 1. Outer subtle blue glow / pulse ring
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 26, 115, 232) // Soft blue glow
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.95f, glowPaint)

        // 2. Crisp White ring
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(3f * density, 0f, 1f * density, Color.argb(80, 0, 0, 0))
        }
        canvas.drawCircle(center, center, center * 0.65f, whitePaint)

        // 3. Vibrant Royal Blue core dot
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A73E8")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.48f, bluePaint)

        // 4. Subtle center white core highlight
        val innerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.18f, innerGlow)

        return bitmap
    }

    /**
     * Generates a modern directional chevron navigation arrow (pointing North / 0°):
     * - Clean chevron arrowhead with white outline
     * - Vibrant blue fill pointing in the vehicle's bearing direction
     */
    fun createDirectionalChevronBitmap(context: Context, sizeDp: Int = 30): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = sizePx.toFloat()
        val h = sizePx.toFloat()

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)    // Top apex tip
            lineTo(w * 0.88f, h * 0.88f)   // Bottom right corner
            lineTo(w * 0.5f, h * 0.68f)    // Inner tail indent
            lineTo(w * 0.12f, h * 0.88f)   // Bottom left corner
            close()
        }

        // White shadow & border outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, outlinePaint)

        // Solid Royal Blue navigation arrow fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A73E8")
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        return bitmap
    }
}
