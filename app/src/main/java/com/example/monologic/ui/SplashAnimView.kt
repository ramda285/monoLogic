package com.example.monologic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos

/**
 * スプラッシュ画面のアニメーションビュー（全フェーズをキャンバス描画）
 *
 * フェーズ構成 (progress 0→1, 総時間 2600ms):
 *   0.00→0.45  Phase 1: ヒマワリ(12L) が sin カーブで左回りに閉じながら
 *                        最終ロゴサイズ(18sp 相当)へスケールダウン
 *   0.45→0.65  Phase 2: "mono" が左から、"ogic" が右からスライドイン
 *   0.65→0.80  Phase 3: "monoLogic" をホールド
 *   0.80→1.00  Phase 4: 全体フェードアウト
 *
 * テキストサイズは MainActivity 左上ロゴ(18sp bold)と同等。
 * Outfit Bold は SplashActivity から setTextTypeface() で注入。
 */
class SplashAnimView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── L 形パス (108×108 座標系, 内角が中心 54,54) ────────────────────
    private val lPath = Path().apply {
        moveTo(44f, 20f)
        lineTo(54f, 20f)
        lineTo(54f, 54f)
        lineTo(82f, 54f)
        lineTo(82f, 64f)
        lineTo(44f, 64f)
        close()
    }

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFB800.toInt()
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEEE.toInt()
        style = Paint.Style.FILL
    }

    /** アニメーション進行度 0.0→1.0 (ValueAnimator で更新) */
    var progress: Float = 0f
        set(value) { field = value; invalidate() }

    /** Outfit Bold などを外部から注入 */
    fun setTextTypeface(tf: Typeface) {
        textPaint.typeface = tf
        invalidate()
    }

    // ── ジオメトリ (onSizeChanged で確定) ────────────────────────────
    private var cx = 0f
    private var cy = 0f

    /**
     * アニメーション開始時の大きいスケール (画面高さの 16%)。
     * Phase 1 の間にここから lScaleFinal へ縮小する。
     */
    private var lScaleAnim  = 1f

    /**
     * 最終ロゴサイズのスケール。
     * MainActivity の 18sp ロゴと同等: textSizePx / 44f
     */
    private var lScaleFinal = 1f

    /** 18sp を px に変換したテキストサイズ */
    private var textSizePx  = 0f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        cx = w / 2f
        cy = h / 2f
        // 26sp: 18sp では小さすぎたため拡大（左上ロゴの約 1.4 倍）
        textSizePx  = 26f * resources.displayMetrics.scaledDensity
        lScaleFinal = textSizePx / 44f          // 最終: 26sp 相当サイズ
        lScaleAnim  = h * 0.20f / 44f           // 開始: 画面高さの 20%
    }

    // ── フェーズ境界 ─────────────────────────────────────────────────
    private val T1  = 0.45f
    private val T2S = 0.45f
    private val T2E = 0.65f
    private val T3E = 0.80f

    // ────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // onSizeChanged 前の早期描画フォールバック
        if (cx == 0f) {
            cx = width / 2f
            cy = height / 2f
            textSizePx  = 18f * resources.displayMetrics.scaledDensity
            lScaleFinal = textSizePx / 44f
            lScaleAnim  = height * 0.20f / 44f
        }

        // マスターアルファ (Phase4 フェードアウト)
        val masterAlpha: Float = if (progress <= T3E) 1f
                                 else 1f - (progress - T3E) / (1f - T3E)

        // ── Phase 1: ヒマワリが左回りに閉じる + スケールダウン ─────────
        val collapseRaw = (progress / T1).coerceIn(0f, 1f)
        // sin カーブ: 最初と最後がゆっくり (ease-in-out)
        val collapse    = easeInOut(collapseRaw)

        // 大きいスケール → 最終スケールへ補間
        val curScale = lerp(lScaleAnim, lScaleFinal, collapse)

        for (i in 0 until 12) {
            val startAngle = i * 30f
            // 左回り: startAngle → 0 へ
            val curAngle = startAngle * (1f - collapse)
            // i=0 は常に不透明、i=1..11 は閉じるにつれてフェードアウト
            val lAlpha = if (i == 0) masterAlpha
                         else masterAlpha * (1f - collapse)
            if (lAlpha < 0.01f) continue

            amberPaint.alpha = (lAlpha * 255).toInt()

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(curScale, curScale)
            canvas.rotate(curAngle)
            canvas.translate(-54f, -54f)
            canvas.drawPath(lPath, amberPaint)
            canvas.restore()
        }

        // ── Phase 2: "mono" と "ogic" がスライドイン ─────────────────
        if (progress > T2S) {
            val textRaw   = ((progress - T2S) / (T2E - T2S)).coerceIn(0f, 1f)
            val textEased = easeOut(textRaw)
            val textAlpha = textEased * masterAlpha

            if (textAlpha > 0.01f) {
                // 最終スケールでの L 外接ボックス
                val lLeft   = cx + (44f - 54f) * lScaleFinal  // cx - 10*s
                val lRight  = cx + (82f - 54f) * lScaleFinal  // cx + 28*s
                val lBottom = cy + (64f - 54f) * lScaleFinal  // cy + 10*s

                textPaint.textSize = textSizePx
                textPaint.alpha    = (textAlpha * 255).toInt()

                val fm       = textPaint.fontMetrics
                // L 底辺 と テキスト降部 を揃える
                val baseline = lBottom - fm.descent

                val slideAmount = width * 0.4f

                // "mono": 右端が lLeft に揃う
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    "mono",
                    lerp(lLeft - slideAmount, lLeft, textEased),
                    baseline,
                    textPaint
                )

                // "ogic": 左端が lRight から始まる
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(
                    "ogic",
                    lerp(lRight + slideAmount, lRight, textEased),
                    baseline,
                    textPaint
                )
            }
        }
    }

    // ── ユーティリティ ──────────────────────────────────────────────
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    /**
     * sin カーブ (ease-in-out): 最初と最後がゆっくり、中間が速い。
     * f(t) = (1 - cos(π·t)) / 2
     */
    private fun easeInOut(t: Float): Float =
        ((1.0 - cos(t * PI)) / 2.0).toFloat()
}
