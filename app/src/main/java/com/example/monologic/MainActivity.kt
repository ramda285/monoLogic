package com.example.monologic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.monologic.data.db.TopicEntity
import com.example.monologic.worker.DailyWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        val app = application as MonoLogicApp

        // 次回投稿時刻を表示
        val (h, m) = app.settingsStore.loadTime()
        findViewById<TextView>(R.id.txtNextSchedule).text =
            "次回投稿：%02d:%02d".format(h, m)

        // お題履歴をDBのFlowから収集してRecyclerViewに表示
        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = TopicAdapter()
        recycler.adapter = adapter
        lifecycleScope.launch {
            app.topicRepository.getAllFlow().collect { topics ->
                adapter.submitList(topics)
            }
        }

        // テスト用：DailyWorkerを即時起動
        findViewById<Button>(R.id.btnRunNow).setOnClickListener {
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())
        }

        // 設定画面へ遷移
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 設定画面から戻ったときに表示時刻を更新
        val (h, m) = (application as MonoLogicApp).settingsStore.loadTime()
        findViewById<TextView>(R.id.txtNextSchedule).text =
            "次回投稿：%02d:%02d".format(h, m)
    }

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

class TopicAdapter : RecyclerView.Adapter<TopicAdapter.VH>() {
    private var items: List<TopicEntity> = emptyList()

    fun submitList(list: List<TopicEntity>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txt: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.txt.text = "${item.date}　${item.word}"
    }

    override fun getItemCount() = items.size
}
