package dev.legendsayantan.extendroid.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.lib.Utils.Companion.getAppNameFromPackage
import dev.legendsayantan.extendroid.data.ActiveSession
import dev.legendsayantan.extendroid.services.ExtendService

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
        val detailsStart =
            "Window ${current.id}" + if (current.mode != ExtendService.Companion.WindowMode.POPUP) ", Port ${current.port}" else ""
        holder.imageType.setImageResource(
            listOf(
                R.drawable.rounded_select_window_24,
                R.drawable.outline_wifi_tethering_24,
                R.drawable.baseline_cast_24
            )[current.mode.ordinal]
        )
        holder.textDetails.text = "$detailsStart - ${current.windowInfo}"
        holder.imageStop.setOnClickListener { onStop(current.id) }
    }
}