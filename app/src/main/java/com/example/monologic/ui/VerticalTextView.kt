package com.example.monologic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 縦書きテキストビュー
 * - fixedTextSizeSp > 0 の場合は固定サイズ（ラベル用）
 * - fixedTextSizeSp == 0 の場合は文字数に応じて自動縮小（メイン単語用）
 * フォント: BIZ UD Gothic を想定。システムフォント san-serif-black で代替。
 * TODO: BIZ UD Gothic の Downloadable Font を設定する場合、
 *       ResourcesCompat.getFont() の結果をここに渡す。
 */
class VerticalTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val charBounds = Rect()

    /** 表示するテキスト */
    var text: String = ""
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** テキスト色 */
    var textColor: Int = Color.BLACK
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    /**
     * 固定フォントサイズ（sp）。0 の場合は文字数に応じて自動スケーリング。
     * ラベル "今日のお題" など小さく固定したい場合に使う。
     */
    var fixedTextSizeSp: Float = 0f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** Typeface を外部からセット可能（Downloadable Font 取得後に使用） */
    fun setFontTypeface(tf: Typeface) {
        paint.typeface = tf
        requestLayout()
        invalidate()
    }

    // ────────────────────────────────────────────
    // 内部計算
    // ────────────────────────────────────────────

    /** 指定された縦方向サイズに対してフォントサイズ(px)を計算 */
    private fun computeFontSizePx(availH: Float): Float {
        if (fixedTextSizeSp > 0f) {
            return fixedTextSizeSp * resources.displayMetrics.scaledDensity
        }
        val n = text.length.coerceAtLeast(1)
        // 上限 90dp
        val maxFontPx = 90f * resources.displayMetrics.density
        // 利用可能な縦サイズの 85% をテキストが使う
        return (availH * 0.85f / n).coerceAtMost(maxFontPx).coerceAtLeast(8f)
    }

    /** 1文字分のセル高さ（ascent/descentベース）を返す */
    private fun cellHeight(fontPx: Float): Float {
        paint.textSize = fontPx
        val fm = paint.fontMetrics
        return fm.descent - fm.ascent
    }

    // ────────────────────────────────────────────
    // Measure
    // ────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hSpec = MeasureSpec.getSize(heightMeasureSpec).toFloat().coerceAtLeast(1f)
        val fontPx = computeFontSizePx(hSpec - paddingTop - paddingBottom)
        paint.textSize = fontPx

        // 横幅 = "国" 1文字の描画幅 + 左右パディング（少し余裕を持たせる）
        paint.getTextBounds("国", 0, 1, charBounds)
        val charW = charBounds.width()
        val desiredW = charW + paddingLeft + paddingRight + 4

        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(hSpec.toInt(), heightMeasureSpec)
        )
    }

    // ────────────────────────────────────────────
    // Draw
    // ────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return

        val availH = (height - paddingTop - paddingBottom).toFloat()
        val availW = (width - paddingLeft - paddingRight).toFloat()

        val fontPx = computeFontSizePx(availH)
        paint.textSize = fontPx
        paint.color = textColor

        val fm = paint.fontMetrics
        val charH = fm.descent - fm.ascent          // 1文字のセル高さ
        val spacing = charH * 0.08f                  // 文字間スペース（8%）
        val n = text.length

        // 縦方向：上詰め（パディングから開始）
        val startBaseline = paddingTop + (-fm.ascent)   // fm.ascent は負値

        val x = paddingLeft + availW / 2f
        var baseline = startBaseline

        for (ch in text) {
            canvas.drawText(ch.toString(), x, baseline, paint)
            baseline += charH + spacing
        }
    }
}
