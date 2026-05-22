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
 * 縦書きテキストビュー（折り返し対応版）
 *
 * - fixedTextSizeSp > 0: 固定サイズ（ラベル用）
 * - fixedTextSizeSp == 0: フォントサイズを自動計算
 *     文字が 1 列に収まらない場合は左方向へ折り返す（日本語縦書きの列進み方向）。
 *     折り返しが起きる基準: フォントサイズが minWrapTextSizeSp を下回るとき。
 */
class VerticalTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = Typeface.create("sans-serif-black", Typeface.NORMAL)
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
     */
    var fixedTextSizeSp: Float = 0f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** Typeface を外部からセット可能 */
    fun setFontTypeface(tf: Typeface) {
        paint.typeface = tf
        requestLayout()
        invalidate()
    }

    // ── 折り返しパラメーター ─────────────────────────────────────────
    /** これより小さくなるなら折り返す（sp）。0 で折り返し無効。 */
    private val minWrapTextSizeSp = 18f

    // ── レイアウトキャッシュ（onMeasure で計算、onDraw で再利用） ─────
    private var cachedFontPx   = 0f
    private var cachedCharsPerCol = 1
    private var cachedNumCols  = 1
    private var cachedColW     = 0f   // 1 列の幅 (px)
    private var cachedColGap   = 0f   // 列間隔 (px)

    // ────────────────────────────────────────────────────────────────────
    // レイアウト計算
    // ────────────────────────────────────────────────────────────────────

    /**
     * フォントサイズを算出し、折り返し列数をキャッシュに保存。
     * @param availH パディング除外済みの縦幅 (px)
     */
    private fun calcLayout(availH: Float) {
        val n     = text.length.coerceAtLeast(1)
        val maxPx = 90f * resources.displayMetrics.density
        val minWrapPx = minWrapTextSizeSp * resources.displayMetrics.scaledDensity

        cachedFontPx = if (fixedTextSizeSp > 0f) {
            fixedTextSizeSp * resources.displayMetrics.scaledDensity
        } else {
            // 1 列に全文字が収まるサイズ
            // 実測の stepH 比率を使って正確に計算
            val samplePx = maxPx
            paint.textSize = samplePx
            val sampleFm = paint.fontMetrics
            val stepRatio = (sampleFm.descent - sampleFm.ascent) * 1.08f / samplePx
            val onColPx = availH / (n * stepRatio)
            // 最小サイズを下回るなら折り返し → minWrapPx を使う
            onColPx.coerceIn(minWrapPx, maxPx)
        }

        paint.textSize = cachedFontPx
        val fm = paint.fontMetrics
        val stepH = (fm.descent - fm.ascent) * 1.08f
        cachedCharsPerCol = (availH / stepH).toInt().coerceAtLeast(1)
        cachedNumCols     = if (text.isEmpty()) 1
                            else (text.length + cachedCharsPerCol - 1) / cachedCharsPerCol

        // 列幅 = "国" の描画幅
        paint.getTextBounds("国", 0, 1, charBounds)
        cachedColW   = charBounds.width().toFloat()
        cachedColGap = cachedColW * 0.25f
    }

    // ────────────────────────────────────────────────────────────────────
    // Measure
    // ────────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h     = MeasureSpec.getSize(heightMeasureSpec).toFloat().coerceAtLeast(1f)
        val availH = h - paddingTop - paddingBottom

        calcLayout(availH)

        // 幅 = 列数 × 列幅 + 列間隔 + パディング
        val desiredW = (cachedColW * cachedNumCols
                + cachedColGap * (cachedNumCols - 1)
                + paddingLeft + paddingRight + 4f).toInt()

        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(h.toInt(), heightMeasureSpec)
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Draw
    // ────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return

        val availH = (height - paddingTop - paddingBottom).toFloat()
        calcLayout(availH)   // サイズ変更があった場合も対応

        paint.color = textColor
        val fm    = paint.fontMetrics
        val stepH = (fm.descent - fm.ascent) * 1.08f
        val availW = (width - paddingLeft - paddingRight).toFloat()

        var charIdx = 0
        for (col in 0 until cachedNumCols) {
            // 日本語縦書き: 1 列目が最右, 折り返しは左へ進む
            val colCenterX = paddingLeft + availW - cachedColW / 2f -
                             col * (cachedColW + cachedColGap)
            var baseline = paddingTop + (-fm.ascent)

            repeat(cachedCharsPerCol) {
                if (charIdx >= text.length) return@repeat
                canvas.drawText(text[charIdx].toString(), colCenterX, baseline, paint)
                baseline += stepH
                charIdx++
            }
        }
    }
}
