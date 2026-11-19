package com.featherx.app.feature.protools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Plain View (no Compose) so the always-on crosshair costs almost no memory. */
class CrosshairView(context: Context) : View(context) {
    private val color = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xCC000000.toInt() }

    var crosshairStyle: CrosshairStyle = CrosshairStyle.DOT
        set(value) { field = value; invalidate() }
    var argb: Int = CrosshairColor.RED.argb
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val span = minOf(width, height).toFloat()
        val line = 2f * d
        color.color = argb

        when (crosshairStyle) {
            CrosshairStyle.DOT -> {
                val r = span * 0.2f
                fill(canvas, cx, cy, r)
            }
            CrosshairStyle.CROSS -> {
                val gap = span * 0.14f
                val arm = span / 2f - line
                stroke(canvas, cx - arm, cy, cx - gap, cy, line)
                stroke(canvas, cx + gap, cy, cx + arm, cy, line)
                stroke(canvas, cx, cy - arm, cx, cy - gap, line)
                stroke(canvas, cx, cy + gap, cx, cy + arm, line)
            }
            CrosshairStyle.CIRCLE -> {
                val ringR = span * 0.42f
                for (p in listOf(outline to line + 2f * d, color to line)) {
                    p.first.style = Paint.Style.STROKE
                    p.first.strokeWidth = p.second
                    canvas.drawCircle(cx, cy, ringR, p.first)
                    p.first.style = Paint.Style.FILL
                }
                fill(canvas, cx, cy, 1.6f * d)
            }
        }
    }

    private fun fill(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val d = resources.displayMetrics.density
        outline.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r + d, outline)
        color.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, color)
    }

    private fun stroke(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        val d = resources.displayMetrics.density
        outline.style = Paint.Style.STROKE
        outline.strokeCap = Paint.Cap.ROUND
        outline.strokeWidth = w + 2f * d
        canvas.drawLine(x1, y1, x2, y2, outline)
        color.style = Paint.Style.STROKE
        color.strokeCap = Paint.Cap.ROUND
        color.strokeWidth = w
        canvas.drawLine(x1, y1, x2, y2, color)
    }
}
