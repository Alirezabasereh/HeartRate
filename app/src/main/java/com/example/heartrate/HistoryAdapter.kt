package com.example.heartrate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** آیتم‌های لیست: کارت خلاصه، سرتیتر تاریخ، ردیف اندازه‌گیری، حالت خالی. */
sealed class HistoryItem {
    data class Summary(val latest: HeartRecord?) : HistoryItem()
    data class Header(val title: String) : HistoryItem()
    data class Row(val record: HeartRecord, val shape: Int) : HistoryItem()
    object Empty : HistoryItem()
}

class HistoryAdapter(
    private val onRecordClick: (HeartRecord) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SUMMARY = 0
        private const val TYPE_HEADER = 1
        private const val TYPE_ROW = 2
        private const val TYPE_EMPTY = 3

        private const val SHAPE_SINGLE = 0
        private const val SHAPE_TOP = 1
        private const val SHAPE_MIDDLE = 2
        private const val SHAPE_BOTTOM = 3
    }

    private var items: List<HistoryItem> = listOf(HistoryItem.Summary(null), HistoryItem.Empty)

    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US)
    private val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)

    class SummaryHolder(v: View) : RecyclerView.ViewHolder(v) {
        val bpmText: TextView = v.findViewById(R.id.lastBpmText)
        val dateText: TextView = v.findViewById(R.id.lastDateText)
        val zoneText: TextView = v.findViewById(R.id.lastZoneText)
        val rangeBar: RangeBarView = v.findViewById(R.id.lastRangeBar)
    }

    class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val dateText: TextView = v.findViewById(R.id.headerDateText)
    }

    class RowHolder(v: View) : RecyclerView.ViewHolder(v) {
        val timeText: TextView = v.findViewById(R.id.itemTimeText)
        val bpmText: TextView = v.findViewById(R.id.itemBpmText)
        val zoneText: TextView = v.findViewById(R.id.itemZoneText)
        val rangeBar: RangeBarView = v.findViewById(R.id.itemRangeBar)
        val divider: View = v.findViewById(R.id.itemDivider)
    }

    class EmptyHolder(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HistoryItem.Summary -> TYPE_SUMMARY
        is HistoryItem.Header -> TYPE_HEADER
        is HistoryItem.Row -> TYPE_ROW
        is HistoryItem.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryHolder(inflater.inflate(R.layout.item_summary, parent, false))
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
            TYPE_ROW -> RowHolder(inflater.inflate(R.layout.item_row, parent, false))
            else -> EmptyHolder(inflater.inflate(R.layout.item_empty, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        when {
            holder is SummaryHolder && item is HistoryItem.Summary -> {
                val latest = item.latest
                if (latest == null) {
                    holder.bpmText.text = "--"
                    holder.dateText.setText(R.string.no_measurement_yet)
                    holder.zoneText.text = ""
                    holder.rangeBar.setBpm(0)
                    holder.itemView.setOnClickListener(null)
                    holder.itemView.isClickable = false
                } else {
                    val zone = HeartZone.of(latest.bpm)
                    holder.bpmText.text = "${latest.bpm}"
                    holder.dateText.text = ctx.getString(
                        R.string.last_measured, dateTimeFormat.format(Date(latest.timestamp))
                    )
                    holder.zoneText.setText(zone.labelRes)
                    holder.zoneText.setTextColor(ContextCompat.getColor(ctx, zone.colorRes))
                    holder.rangeBar.setBpm(latest.bpm)
                    holder.itemView.setOnClickListener { onRecordClick(latest) }
                }
            }

            holder is HeaderHolder && item is HistoryItem.Header -> {
                holder.dateText.text = item.title
            }

            holder is RowHolder && item is HistoryItem.Row -> {
                val zone = HeartZone.of(item.record.bpm)
                holder.timeText.text = timeFormat.format(Date(item.record.timestamp))
                holder.bpmText.text = "${item.record.bpm}"
                holder.zoneText.setText(zone.labelRes)
                holder.zoneText.setTextColor(ContextCompat.getColor(ctx, zone.colorRes))
                holder.rangeBar.setBpm(item.record.bpm)
                holder.itemView.setOnClickListener { onRecordClick(item.record) }

                // Card corners for the group: top / middle / bottom / single
                val bg = when (item.shape) {
                    SHAPE_TOP -> R.drawable.bg_card_top
                    SHAPE_MIDDLE -> R.drawable.bg_card_middle
                    SHAPE_BOTTOM -> R.drawable.bg_card_bottom
                    else -> R.drawable.bg_card_single
                }
                holder.itemView.setBackgroundResource(bg)
                holder.divider.visibility =
                    if (item.shape == SHAPE_TOP || item.shape == SHAPE_MIDDLE) View.VISIBLE else View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(records: List<HeartRecord>) {
        items = buildItems(records)
        notifyDataSetChanged()
    }

    /** رکوردها (جدیدترین اول) را بر اساس روز گروه‌بندی می‌کند. */
    private fun buildItems(records: List<HeartRecord>): List<HistoryItem> {
        val out = mutableListOf<HistoryItem>(HistoryItem.Summary(records.firstOrNull()))
        if (records.isEmpty()) {
            out.add(HistoryItem.Empty)
            return out
        }

        val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val titleFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.US)

        var i = 0
        while (i < records.size) {
            val key = dayKeyFormat.format(Date(records[i].timestamp))
            var j = i
            while (j < records.size && dayKeyFormat.format(Date(records[j].timestamp)) == key) j++

            out.add(HistoryItem.Header(titleFormat.format(Date(records[i].timestamp))))
            val n = j - i
            for (k in 0 until n) {
                val shape = when {
                    n == 1 -> SHAPE_SINGLE
                    k == 0 -> SHAPE_TOP
                    k == n - 1 -> SHAPE_BOTTOM
                    else -> SHAPE_MIDDLE
                }
                out.add(HistoryItem.Row(records[i + k], shape))
            }
            i = j
        }
        return out
    }
}
