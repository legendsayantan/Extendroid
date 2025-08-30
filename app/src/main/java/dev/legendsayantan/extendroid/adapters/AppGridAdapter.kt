package dev.legendsayantan.extendroid.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.model.AppItem


/**
 * @author legendsayantan
 */
// RecyclerView Adapter for a grid layout
class AppGridAdapter(
    private val packages: List<AppItem>,
    private val onItemClick:(String)-> Unit
) : RecyclerView.Adapter<AppGridAdapter.ViewHolder>() {

    var preselected: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = packages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(packages[position], onItemClick, preselected)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logoImageView: ImageView = itemView.findViewById(R.id.logo)
        private val nameTextView: TextView = itemView.findViewById(R.id.appName)

        fun bind(item: AppItem, onItemClick: (String) -> Unit, preselected: Set<String>) {
            logoImageView.setImageDrawable(item.image)
            nameTextView.text = item.appName

            // Highlight if selected
            itemView.findViewById<ImageView>(R.id.selected).isVisible = preselected.contains(item.packageName)

            itemView.setOnClickListener {
                onItemClick(item.packageName)
            }
        }
    }
}
