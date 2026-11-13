package com.example.heartrate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Dedicated page for a single heart-rate record. */
class RecordDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_BPM = "bpm"
        private const val EXTRA_TIMESTAMP = "timestamp"

        fun intent(context: Context, record: HeartRecord): Intent =
            Intent(context, RecordDetailActivity::class.java)
                .putExtra(EXTRA_BPM, record.bpm)
                .putExtra(EXTRA_TIMESTAMP, record.timestamp)
    }

    private var timestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_detail)

        val bpm = intent.getIntExtra(EXTRA_BPM, 0)
        timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)
        val zone = HeartZone.of(bpm)
        val date = Date(timestamp)

        findViewById<TextView>(R.id.detailBpmText).text = "$bpm"
        findViewById<TextView>(R.id.detailZoneText).apply {
            setText(zone.labelRes)
            setTextColor(ContextCompat.getColor(this@RecordDetailActivity, zone.colorRes))
        }
        findViewById<RangeBarView>(R.id.detailRangeBar).setBpm(bpm)
        findViewById<TextView>(R.id.detailDateText).text =
            DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(date)
        findViewById<TextView>(R.id.detailTimeText).text =
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.menuButton).setOnClickListener { showMenu(it) }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.delete_record)
        popup.setOnMenuItemClickListener {
            confirmDelete()
            true
        }
        popup.show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_record_title)
            .setMessage(R.string.delete_record_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                HeartRateStorage.deleteRecord(this, timestamp)
                finish()
            }
            .show()
    }
}
