package com.example.monologic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        requestNotificationPermissionIfNeeded()

        val app = application as MonoLogicApp

        val (h, m) = app.settingsStore.loadTime()
        findViewById<TextView>(R.id.txtNextSchedule).text = "%02d:%02d".format(h, m)

        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = TopicAdapter()
        recycler.adapter = adapter

        lifecycleScope.launch {
            app.topicRepository.getAllFlow().collect { topics ->
                adapter.submitList(topics)
            }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.btnRunNow).setOnClickListener {
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        val (h, m) = (application as MonoLogicApp).settingsStore.loadTime()
        findViewById<TextView>(R.id.txtNextSchedule).text = "%02d:%02d".format(h, m)
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
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtWord: TextView = view.findViewById(R.id.txtWord)
        val imgPosted: ImageView = view.findViewById(R.id.imgPosted)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topic, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.txtDate.text = item.date
        holder.txtWord.text = item.word
        holder.imgPosted.visibility =
            if (item.blueskyPostUri != null) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = items.size
}
