package com.example.monologic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos

/**
 * スプラッシュ画面のアニメーションビュー（全フェーズをキャンバス描画）
 *
 * フェーズ構成 (progress 0→1, 総時間 2600ms):
 *   0.00→0.38  Phase 1: L#1(30°位置) がスイーパーとして CCW に回転し、
 *                        通過した L を順次巻き込む（i=0→11→10→…→2 の順）
 *                        スイープ総角度 -390° (= -360° - 30°) → 終了時に 0° に着地
 *   0.38→0.55  Phase 2: 残った L が CCW に追加 -360° スピン (easeOut)
 *   0.55→0.72  Phase 3: "mono" が左から、"ogic" が右からスライドイン
 *   0.72→0.83  Phase 4: "monoLogic" をホールド
 *   0.83→1.00  Phase 5: 全体フェードアウト
 *
 * L・mono・ogic すべて Outfit Bold フォントを使用。
 * SplashActivity から setTextTypeface() で注入する。
 */
class SplashAnimView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** アンバー色ペイント（L 字描画用） */
    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0xFFFFB800.toInt()
        style     = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    /** グレーペイント（"mono" / "ogic" テキスト用） */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEEE.toInt()
        style = Paint.Style.FILL
    }

    /** アニメーション進行度 0.0→1.0 (ValueAnimator で更新) */
    var progress: Float = 0f
        set(value) { field = value; invalidate() }

    /** Outfit Bold などを外部から注入（L と mono/ogic 両方に適用） */
    fun setTextTypeface(tf: Typeface) {
        amberPaint.typeface = tf
        textPaint.typeface  = tf
        invalidate()
    }

    // ── ジオメトリ (onSizeChanged で確定) ────────────────────────────
    private var cx = 0f
    private var cy = 0f

    /** アニメーション開始時の大きいテキストサイズ (画面高さの 20%) */
    private var lTextSizeAnim = 1f

    /** 最終ロゴテキストサイズ (26sp) */
    private var textSizePx = 0f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        cx = w / 2f
        cy = h / 2f
        textSizePx    = 26f * resources.displayMetrics.scaledDensity
        lTextSizeAnim = h * 0.20f
    }

    // ── スイーパー設定 ────────────────────────────────────────────────
    /** 最初にスイープを開始する L のインデックス（i=11 → 330°位置） */
    private val SWEEPER_IDX   = 11
    private val SWEEPER_START = SWEEPER_IDX * 30f   // 330°

    /**
     * Phase 1 のスイープ総回転角。
     * 330° から CCW に -330° 回転するとちょうど 0° に着地する。
     * 巻き込み順: i=10→9→8→…→0（330°から CCW 順）
     */
    private val SWEEP_TOTAL = -330f

    // ── フェーズ境界 ─────────────────────────────────────────────────
    private val T1      = 0.38f   // Phase 1 (スイープ) 終了
    private val TSPIN_S = 0.38f   // Phase 2 (スピン)   開始
    private val TSPIN_E = 0.55f   // Phase 2 (スピン)   終了
    private val T2S     = 0.55f   // Phase 3 (テキスト) 開始
    private val T2E     = 0.72f   // Phase 3 (テキスト) 終了
    private val T3E     = 0.83f   // Phase 4 ホールド終了 → フェードアウト開始

    // ────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // onSizeChanged 前の早期描画フォールバック
        if (cx == 0f) {
            cx = width / 2f
            cy = height / 2f
            textSizePx    = 18f * resources.displayMetrics.scaledDensity
            lTextSizeAnim = height * 0.20f
        }

        // ── マスターアルファ (Phase5 フェードアウト) ──────────────────
        val masterAlpha: Float = if (progress <= T3E) 1f
                                 else 1f - (progress - T3E) / (1f - T3E)

        // ── Phase 1: スイーパーが CCW に SWEEP_TOTAL だけ回転 ──────────
        val sweepRaw   = (progress / T1).coerceIn(0f, 1f)
        val sweepEased = easeIn(sweepRaw)
        val sweepAngle = SWEEP_TOTAL * sweepEased  // 0 → -390°

        // ── Phase 2: 追加 -360° スピン ────────────────────────────────
        val spinRaw   = ((progress - TSPIN_S) / (TSPIN_E - TSPIN_S)).coerceIn(0f, 1f)
        val spinEased = easeOut(spinRaw)
        val spinAngle = -360f * spinEased           // 0 → -360°

        // スイーパーの絶対角（Phase1+2 累積）
        // Phase1終了: 30 + (-390) = -360° ≡ 0° ✓
        // Phase2終了: -360 + (-360) = -720° ≡ 0° ✓
        val sweeperAngle = SWEEPER_START + sweepAngle + spinAngle

        // L テキストサイズ（Phase 1 中に大→小、Phase 2 以降は固定）
        val curTextSize = lerp(lTextSizeAnim, textSizePx, sweepEased)
        amberPaint.textSize = curTextSize
        val lFm            = amberPaint.fontMetrics
        val lCenterOffsetY = -(lFm.ascent + lFm.descent) / 2f

        // ── 非スイーパー L を描画 ─────────────────────────────────────
        // スイーパーが SWEEPER_START から CCW に進む順で各Lを巻き込む。
        // L#i の吸収に必要な CCW 移動量 = (SWEEPER_START - i*30°) mod 360°（正値化）
        val fadeRange = 25f  // 吸収角の手前何度からフェードアウトするか

        for (i in 0 until 12) {
            if (i == SWEEPER_IDX) continue  // スイーパー本体はあとで描画

            // CCW でスイーパーが L#i の位置に到達するまでの移動量（正値、0°超）
            var travel = (SWEEPER_START - i * 30f) % 360f
            if (travel <= 0f) travel += 360f

            val absorbAngle  = -travel               // 吸収が起きる sweepAngle 値
            val distToAbsorb = sweepAngle - absorbAngle
            // distToAbsorb > fadeRange: 完全表示
            // 0 < distToAbsorb ≤ fadeRange: フェードアウト
            // distToAbsorb ≤ 0: 巻き込み済み（非表示）
            val lAlpha = (distToAbsorb / fadeRange).coerceIn(0f, 1f) * masterAlpha

            if (lAlpha < 0.01f) continue

            amberPaint.alpha = (lAlpha * 255).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(i * 30f)
            canvas.drawText("L", 0f, lCenterOffsetY, amberPaint)
            canvas.restore()
        }

        // ── スイーパー L を描画（常に不透明） ─────────────────────────
        amberPaint.alpha = (masterAlpha * 255).toInt()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(sweeperAngle)
        canvas.drawText("L", 0f, lCenterOffsetY, amberPaint)
        canvas.restore()

        // ── Phase 3: "mono" と "ogic" がスライドイン ──────────────────
        if (progress > T2S) {
            val textRaw   = ((progress - T2S) / (T2E - T2S)).coerceIn(0f, 1f)
            val textEased = easeOut(textRaw)
            val textAlpha = textEased * masterAlpha

            if (textAlpha > 0.01f) {
                // 最終スケールでの L のサイズ・位置を計算
                amberPaint.textSize = textSizePx
                val finalFm   = amberPaint.fontMetrics
                val lBaseline = cy - (finalFm.ascent + finalFm.descent) / 2f
                val lHalfW    = amberPaint.measureText("L") / 2f
                val lLeft     = cx - lHalfW
                val lRight    = cx + lHalfW

                textPaint.textSize = textSizePx
                textPaint.alpha    = (textAlpha * 255).toInt()

                val slideAmount = width * 0.4f

                // "mono": 右端が lLeft に揃う
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    "mono",
                    lerp(lLeft - slideAmount, lLeft, textEased),
                    lBaseline,
                    textPaint
                )

                // "ogic": 左端が lRight から始まる
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(
                    "ogic",
                    lerp(lRight + slideAmount, lRight, textEased),
                    lBaseline,
                    textPaint
                )
            }
        }
    }

    // ── ユーティリティ ──────────────────────────────────────────────
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    /** 加速のみ: 最初ゆっくり → 末尾で最高速 */
    private fun easeIn(t: Float): Float  = t * t
    /** 減速のみ: 最初から最高速 → 末尾でゆっくり停止 */
    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
    /** sin カーブ (ease-in-out) */
    @Suppress("unused")
    private fun easeInOut(t: Float): Float =
        ((1.0 - cos(t * PI)) / 2.0).toFloat()
}
