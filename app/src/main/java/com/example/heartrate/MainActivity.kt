package com.example.heartrate

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var lastBpmText: TextView
    private lateinit var lastDateText: TextView
    private lateinit var emptyHistoryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lastBpmText = findViewById(R.id.lastBpmText)
        lastDateText = findViewById(R.id.lastDateText)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)

        val recyclerView: RecyclerView = findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(emptyList())
        recyclerView.adapter = adapter

        findViewById<View>(R.id.startMeasurementButton).setOnClickListener {
            startActivity(Intent(this, MeasurementActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    private fun refreshHistory() {
        val records = HeartRateStorage.getRecords(this)
        adapter.updateData(records)

        if (records.isNotEmpty()) {
            val latest = records[0]
            lastBpmText.text = "${latest.bpm}"
            val sdf = SimpleDateFormat("yyyy/MM/dd  -  HH:mm", Locale.getDefault())
            lastDateText.text = sdf.format(Date(latest.timestamp))
            emptyHistoryText.visibility = View.GONE
        } else {
            lastBpmText.text = "--"
            lastDateText.text = "هنوز اندازه‌گیری ثبت نشده"
            emptyHistoryText.visibility = View.VISIBLE
        }
    }
}
