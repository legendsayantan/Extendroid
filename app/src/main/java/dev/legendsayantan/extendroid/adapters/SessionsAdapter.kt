package dev.legendsayantan.extendroid.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils.Companion.getAppNameFromPackage
import dev.legendsayantan.extendroid.Utils.Companion.getForegroundColor
import dev.legendsayantan.extendroid.data.ActiveSession

/**
 * @author legendsayantan
 */
class SessionsAdapter(
    val context: Context,
    val data: List<ActiveSession>,
    val onStop: (Int) -> Unit
) : RecyclerView.Adapter<SessionsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName = view.findViewById<TextView>(R.id.appName)
        val textDetails = view.findViewById<TextView>(R.id.windowDetails)
        val imageType = view.findViewById<ImageView>(R.id.windowType)
        val imageStop = view.findViewById<ImageView>(R.id.stop)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val current = data[position]
        holder.textName.text = context.getAppNameFromPackage(current.pkg)
        var detailsStart = ""
        if (current.port != -1) {
            holder.imageType.setImageResource(R.drawable.outline_wifi_tethering_24)
            detailsStart = "Port ${current.port}"
        } else {
            detailsStart = "Window ${current.id}"
        }
        holder.textDetails.text = "$detailsStart - ${current.windowInfo}"
        holder.imageStop.setOnClickListener { onStop(current.id) }
    }
}