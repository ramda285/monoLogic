package com.example.monologic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monologic.analysis.GeminiTopicAnalyzer
import com.example.monologic.analysis.KeywordEntry
import com.example.monologic.analysis.Sentiment
import com.example.monologic.data.db.ReplyStatus
import com.example.monologic.data.db.TopicEntity
import com.example.monologic.ui.VerticalTextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        setupLogo()
        setupSettingsButton()
        setupBottomSheet()
        observeTopics()
    }

    // ────────────────────────────────────────────────────
    // ロゴ: Outfit Bold フォント + "mono[L]ogic" の L だけアンバー色
    // ────────────────────────────────────────────────────
    private fun setupLogo() {
        val tv = findViewById<TextView>(R.id.tvLogo)
        loadOutfitFont()?.let { tv.typeface = it }

        val full = "monoLogic"
        val spannable = SpannableString(full)
        val lIndex = full.indexOf('L')
        spannable.setSpan(
            ForegroundColorSpan(0xFFFFB800.toInt()),
            lIndex, lIndex + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = spannable
    }

    private fun loadOutfitFont(): Typeface? {
        try {
            val tf = ResourcesCompat.getFont(this, R.font.outfit_bold)
            if (tf != null) {
                Log.d("OutfitFont", "Loaded from Google Fonts cache")
                return tf
            }
        } catch (e: Exception) {
            Log.w("OutfitFont", "res/font/outfit_bold unavailable: ${e.message}")
        }
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/Outfit-Bold.ttf")
            Log.d("OutfitFont", "Loaded from assets/fonts/Outfit-Bold.ttf")
            return tf
        } catch (e: Exception) {
            Log.w("OutfitFont", "Outfit-Bold.ttf not in assets: ${e.message}")
        }
        Log.e("OutfitFont", "Outfit font unavailable — using system font.")
        return null
    }

    // ────────────────────────────────────────────────────
    // 設定ボタン
    // ────────────────────────────────────────────────────
    private fun setupSettingsButton() {
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ────────────────────────────────────────────────────
    // ボトムシート
    // ────────────────────────────────────────────────────
    private fun setupBottomSheet() {
        val sheet = findViewById<View>(R.id.bottomSheet)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = HistoryAdapter()
    }

    // ────────────────────────────────────────────────────
    // お題を DB から観察
    // ────────────────────────────────────────────────────
    private fun observeTopics() {
        val wordView  = findViewById<VerticalTextView>(R.id.tvTopicWord)
        val labelView = findViewById<VerticalTextView>(R.id.tvTopicLabel)
        val adapter   = (findViewById<RecyclerView>(R.id.recyclerHistory).adapter as HistoryAdapter)

        // 「今日のお題」ラベル: 固定 9sp、グレー
        labelView.fixedTextSizeSp = 9f
        labelView.textColor = ContextCompat.getColor(this, R.color.colorTopicLabel)
        labelView.text = "今日のお題"

        // お題単語の色
        wordView.textColor = ContextCompat.getColor(this, R.color.colorTopicWord)

        // 初回表示用フローインアニメーションの初期状態
        wordView.alpha = 0f
        wordView.translationY = -(resources.displayMetrics.density * 120f)

        var topicAnimPlayed = false

        lifecycleScope.launch {
            (application as MonoLogicApp).topicRepository.getAllFlow().collect { topics ->
                if (topics.isNotEmpty()) {
                    // 最新のお題をメイン表示
                    wordView.text = topics.first().word
                    // キーワードチップ更新
                    updateKeywordChips(parseKeywords(topics.first().keywords))
                    // 残りを履歴へ（先頭 = 今日分 を除く）
                    adapter.submitList(topics.drop(1))

                    // 初回のみ上からフローイン
                    if (!topicAnimPlayed) {
                        topicAnimPlayed = true
                        wordView.postDelayed({
                            wordView.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(600)
                                .setStartDelay(0)
                                .setInterpolator(DecelerateInterpolator(2f))
                                .start()
                        }, 150L)
                    }
                } else {
                    wordView.text = "ー"
                    updateKeywordChips(null)
                    adapter.submitList(emptyList())
                    // データなしの場合もフェードイン
                    if (!topicAnimPlayed) {
                        topicAnimPlayed = true
                        wordView.postDelayed({
                            wordView.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(400)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }, 150L)
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────
    // キーワードチップ表示
    // ────────────────────────────────────────────────────
    private fun updateKeywordChips(keywords: List<KeywordEntry>?) {
        val layout = findViewById<View>(R.id.layoutKeywords)
        val chips  = listOf(
            findViewById<TextView>(R.id.tvChip1),
            findViewById<TextView>(R.id.tvChip2),
            findViewById<TextView>(R.id.tvChip3)
        )
        if (keywords.isNullOrEmpty()) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        keywords.forEachIndexed { i, kw ->
            val chip = chips.getOrNull(i) ?: return@forEachIndexed
            chip.visibility = View.VISIBLE
            chip.text = kw.word
            val (bgRes, textColorRes) = when (kw.sentiment) {
                Sentiment.POSITIVE -> R.drawable.bg_chip_positive to R.color.colorChipPositiveText
                Sentiment.NEGATIVE -> R.drawable.bg_chip_negative to R.color.colorChipNegativeText
                else               -> R.drawable.bg_chip_neutral  to R.color.colorChipNeutralText
            }
            chip.setBackgroundResource(bgRes)
            chip.setTextColor(ContextCompat.getColor(this, textColorRes))
            chip.setOnClickListener {
                startActivity(
                    Intent(this, MindmapActivity::class.java)
                        .putExtra(MindmapActivity.EXTRA_WORD, kw.word)
                )
            }
        }
        for (i in keywords.size until chips.size) chips[i].visibility = View.GONE
    }

    private fun parseKeywords(json: String?): List<KeywordEntry>? {
        json ?: return null
        return try {
            Json.decodeFromString<List<KeywordEntry>>(json)
        } catch (e: Exception) {
            null
        }
    }

    // ────────────────────────────────────────────────────
    // 通知権限
    // ────────────────────────────────────────────────────
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }
}

// ────────────────────────────────────────────────────────
// 履歴リストのアダプター
// ────────────────────────────────────────────────────────
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var items: List<TopicEntity> = emptyList()

    fun submitList(list: List<TopicEntity>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvHistoryWord)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvDot:  TextView = view.findViewById(R.id.tvHistoryDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvWord.text = item.word
        holder.tvDate.text = item.date

        // リプライステータスドット
        when (item.replyStatus) {
            ReplyStatus.REPLIED -> {
                val keywords = item.keywords?.let {
                    try { Json.decodeFromString<List<KeywordEntry>>(it) }
                    catch (e: Exception) { null }
                }
                val majority = keywords?.let { GeminiTopicAnalyzer("").majoritySentiment(it) }
                             ?: Sentiment.NEUTRAL
                val dotRes = when (majority) {
                    Sentiment.POSITIVE -> R.drawable.bg_dot_positive
                    Sentiment.NEGATIVE -> R.drawable.bg_dot_negative
                    else               -> R.drawable.bg_dot_neutral
                }
                holder.tvDot.setBackgroundResource(dotRes)
                holder.tvDot.text = ""
                holder.tvDot.visibility = View.VISIBLE
            }
            ReplyStatus.TIMEOUT -> {
                holder.tvDot.setBackgroundResource(0)
                holder.tvDot.text = "✕"
                holder.tvDot.setTextColor(0xFF888888.toInt())
                holder.tvDot.textSize = 9f
                holder.tvDot.visibility = View.VISIBLE
            }
            else -> holder.tvDot.visibility = View.INVISIBLE
        }
    }

    override fun getItemCount() = items.size
}
