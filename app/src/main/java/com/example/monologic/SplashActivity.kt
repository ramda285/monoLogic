package com.example.monologic

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.monologic.ui.SplashAnimView

/**
 * スプラッシュアニメーションを再生するランチャーActivity。
 * アニメーション完了後に MainActivity へ遷移して自身は finish() する。
 *
 * アニメーションシーケンス:
 *  1. ヒマワリ(12L)が左回りに閉じて1本のLに収束
 *  2. "mono" が左から、"ogic" が右からスライドインして "monoLogic" が完成
 *  3. 全体フェードアウト → MainActivity 起動
 */
class SplashActivity : AppCompatActivity() {

    private val ANIM_DURATION_MS = 2600L

    /**
     * Outfit Bold を取得する。優先順:
     *  1. res/font/outfit_bold.xml (Google Fonts downloadable, キャッシュ済みの場合即時返却)
     *  2. assets/fonts/Outfit-Bold.ttf (手動配置した TTF)
     *  3. null → システムフォントで代替
     *
     * フォントが表示されない場合は Logcat "OutfitFont" タグを確認してください。
     */
    private fun loadOutfitFont(): Typeface? {
        // 1. Google Fonts (キャッシュ済みなら即時利用可能)
        try {
            val tf = ResourcesCompat.getFont(this, R.font.outfit_bold)
            if (tf != null) {
                Log.d("OutfitFont", "Loaded from Google Fonts cache (res/font/outfit_bold)")
                return tf
            }
        } catch (e: Exception) {
            Log.w("OutfitFont", "res/font/outfit_bold unavailable: ${e.message}")
        }
        // 2. assets/fonts/ に手動配置された TTF
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/Outfit-Bold.ttf")
            Log.d("OutfitFont", "Loaded from assets/fonts/Outfit-Bold.ttf")
            return tf
        } catch (e: Exception) {
            Log.w("OutfitFont", "assets/fonts/Outfit-Bold.ttf not found: ${e.message}")
        }
        Log.e("OutfitFont", "Outfit font unavailable — using system font. " +
            "Place Outfit-Bold.ttf in app/src/main/assets/fonts/ to fix this.")
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val animView = SplashAnimView(this)
        setContentView(animView)

        // Outfit Bold を取得（Google Fonts キャッシュ → assets フォールバック）
        loadOutfitFont()?.let { animView.setTextTypeface(it) }

        // ValueAnimator でアニメーション進行度 0→1 を送り込む
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration    = ANIM_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animView.progress = it.animatedValue as Float }
        }
        animator.start()

        // アニメーション完了後に MainActivity へ遷移
        animView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, 0)
            finish()
        }, ANIM_DURATION_MS + 80L)
    }
}
