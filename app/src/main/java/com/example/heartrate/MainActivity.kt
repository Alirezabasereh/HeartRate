package com.example.heartrate

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter()
        recyclerView.adapter = adapter

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.menuButton).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.startMeasurementButton).setOnClickListener {
            startActivity(Intent(this, MeasurementActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.updateData(HeartRateStorage.getRecords(this))
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.clear_history)
        popup.setOnMenuItemClickListener {
            confirmClearHistory()
            true
        }
        popup.show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_confirm_title)
            .setMessage(R.string.clear_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                HeartRateStorage.clearAll(this)
                adapter.updateData(emptyList())
            }
            .show()
    }
}
