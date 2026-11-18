package com.wakeme.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Utility to replace OsmDroid's legacy "man in yellow" icon with a modern,
 * medium-sized Google Maps / Uber style glowing blue GPS dot and directional navigation chevron.
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
