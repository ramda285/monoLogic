package com.example.monologic

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.monologic.worker.WorkScheduler

class SettingsActivity : AppCompatActivity() {
    private lateinit var editHandle: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnTimePicker: Button
    private var selectedHour = 8
    private var selectedMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editHandle = findViewById(R.id.editHandle)
        editPassword = findViewById(R.id.editPassword)
        btnTimePicker = findViewById(R.id.btnTimePicker)

        val app = application as MonoLogicApp

        // 保存済み認証情報を反映（ハンドルを表示、パスワードはマスク済みヒントで示す）
        app.credentialStore.loadCredentials()?.let { (handle, _) ->
            editHandle.setText(handle)
            editPassword.hint = "（保存済み — 変更する場合のみ入力）"
        }

        // 保存済み投稿時刻を反映（回転後はsavedInstanceStateから復元）
        val (h, m) = savedInstanceState?.let {
            it.getInt("hour") to it.getInt("minute")
        } ?: app.settingsStore.loadTime()
        selectedHour = h
        selectedMinute = m
        updateTimeButton()

        btnTimePicker.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                updateTimeButton()
            }, selectedHour, selectedMinute, true).show()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val handle = editHandle.text.toString().trim()
            val password = editPassword.text.toString().trim()
            // ハンドルとパスワードの両方が入力されている場合のみ認証情報を更新
            if (handle.isNotEmpty() && password.isNotEmpty()) {
                app.credentialStore.saveCredentials(handle, password)
            }
            app.settingsStore.saveTime(selectedHour, selectedMinute)
            WorkScheduler.schedule(this, selectedHour, selectedMinute)
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("hour", selectedHour)
        outState.putInt("minute", selectedMinute)
    }

    private fun updateTimeButton() {
        btnTimePicker.text = "%02d:%02d".format(selectedHour, selectedMinute)
    }
}
