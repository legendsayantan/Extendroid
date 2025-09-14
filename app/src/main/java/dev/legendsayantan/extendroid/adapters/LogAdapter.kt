package dev.legendsayantan.extendroid.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.lib.Logging.Level;
import dev.legendsayantan.extendroid.R;

class LogAdapter(
    initialLogs: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val items = mutableListOf<LogEntry>()

    init {
        setLogs(initialLogs)
    }

    data class LogEntry(val time: String, val level: Level, val message: String)

    // Material-ish colors (RGB hex without '#'). We'll prepend 20% alpha (0x33).
    private val levelColor = mapOf(
        Level.INFO to "2196F3",         // blue
        Level.ERROR to "F44336",        // red
        Level.DEBUG to "4CAF50",        // green
        Level.NOTIFICATION to "9C27B0"  // purple
    )
    private val levelEmoji = mapOf(
        Level.INFO to "\u2139\uFE0F",         // ℹ️
        Level.ERROR to "\u26D4\uFE0F",        // ⛔
        Level.DEBUG to "\uD83D\uDD27",        // 🔧
        Level.NOTIFICATION to "\uD83D\uDD14"  // 🔔
    )

    fun setLogs(logs: Map<String, String>) {
        items.clear()
        // Convert map to list of LogEntry. Keep insertion order of the map.
        logs.forEach { (key, value) ->
            val parts = key.split("|")
            val timePart = parts.getOrNull(0) ?: key
            val levelKey = parts.getOrNull(1)
            val level = Level.fromKey(levelKey) ?: Level.INFO
            items.add(LogEntry(timePart, level, value))
        }
        notifyDataSetChanged()
    }

    fun updateLogs(logs: Map<String, String>) {
        setLogs(logs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.layout_item_log, parent, false)
        return LogViewHolder(v)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout? = itemView.findViewById(R.id.container)
        private val timeTv: TextView = itemView.findViewById(R.id.time)
        private val levelTv: TextView = itemView.findViewById(R.id.level)
        private val contentTv: TextView = itemView.findViewById(R.id.content)

        fun bind(entry: LogEntry) {
            // Show time part as-is
            timeTv.text = entry.time

            // Show full name of the level (uppercase name from enum)
            levelTv.text = "${entry.level.name} ${levelEmoji[entry.level]}"

            // Show message
            contentTv.text = entry.message

            // Apply 20% opaque background color based on level
            val rgb = levelColor[entry.level] ?: "CCCCCC"
            // 0x33 hex ~= 20% alpha
            val colorString = "#33$rgb"
            try {
                container?.setBackgroundColor(Color.parseColor(colorString))
            } catch (e: IllegalArgumentException) {
                // fallback to light gray translucent if parse fails
                container?.setBackgroundColor(Color.parseColor("#33CCCCCC"))
            }
        }
    }
}
