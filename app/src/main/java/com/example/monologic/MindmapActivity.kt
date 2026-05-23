package com.example.monologic

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MindmapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORD = "extra_word"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mindmap)
        val word = intent.getStringExtra(EXTRA_WORD) ?: ""
        findViewById<TextView>(R.id.tvMindmapWord).text = word
        supportActionBar?.apply {
            title = "マインドマップ"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
