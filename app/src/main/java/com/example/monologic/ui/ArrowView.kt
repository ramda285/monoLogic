package com.example.monologic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Canvas で → を描画するシンプルな View */
class ArrowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFFAAAAAA.toInt()
        strokeWidth = 2f * resources.displayMetrics.density
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val hw = width * 0.35f
        val hh = height * 0.25f
        canvas.drawLine(cx - hw, cy, cx + hw, cy, paint)
        val path = Path().apply {
            moveTo(cx + hw - hh, cy - hh)
            lineTo(cx + hw, cy)
            lineTo(cx + hw - hh, cy + hh)
        }
        canvas.drawPath(path, paint)
    }
}
