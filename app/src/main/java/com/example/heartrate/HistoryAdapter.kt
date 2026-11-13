package com.example.heartrate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<HeartRecord>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.itemDot)
        val bpmText: TextView = view.findViewById(R.id.itemBpmText)
        val dateText: TextView = view.findViewById(R.id.itemDateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bpmText.text = "${item.bpm} BPM"

        val sdf = SimpleDateFormat("yyyy/MM/dd  -  HH:mm", Locale.getDefault())
        holder.dateText.text = sdf.format(Date(item.timestamp))

        val color = when {
            item.bpm < 60 -> Color.parseColor("#3DDC97")   // سبز: آرام
            item.bpm <= 100 -> Color.parseColor("#FFB84D")  // نارنجی: نرمال
            else -> Color.parseColor("#FF4365")             // قرمز: بالا
        }
        holder.dot.background.setTint(color)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<HeartRecord>) {
        items = newItems
        notifyDataSetChanged()
    }
}
