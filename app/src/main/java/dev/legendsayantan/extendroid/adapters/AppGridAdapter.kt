package dev.legendsayantan.extendroid.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.model.AppItem


/**
 * @author legendsayantan
 */
// RecyclerView Adapter for a grid layout
class AppGridAdapter(
    private val context: Context,
    private val packages: List<AppItem>,
    private val onItemClick: (String,()->Unit) -> Unit= {a,b->},
    private val onItemLongClick: (String,()->Unit) -> Unit = {a,b->}
) : RecyclerView.Adapter<AppGridAdapter.ViewHolder>() {

    var preselected: Set<String> = emptySet()
    var showPinnedAtTop = false
    var prepinned: Set<String> = Prefs(context).pinnedApps

    val refreshCallback = {
        prepinned = Prefs(context).pinnedApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = packages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            packages[position],
            { onItemClick(it,refreshCallback) },
            { onItemLongClick(it,refreshCallback) },
            preselected.contains(packages[position].packageName),
            showPinnedAtTop && prepinned.contains(packages[position].packageName)
        )
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logoImageView: ImageView = itemView.findViewById(R.id.logo)
        private val nameTextView: TextView = itemView.findViewById(R.id.appName)
        private val pinnedIcon: ImageView = itemView.findViewById(R.id.pinned)

        fun bind(
            item: AppItem,
            onItemClick: (String) -> Unit,
            onItemLongClick: (String) -> Unit = {a->},
            isPreSelected: Boolean = false,
            isPinned: Boolean = false
        ) {
            logoImageView.setImageDrawable(item.image)
            nameTextView.text = item.appName

            // Highlight if selected
            itemView.findViewById<ImageView>(R.id.selected).isVisible = isPreSelected
            pinnedIcon.visibility = if (isPinned) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick(item.packageName)
            }
            itemView.setOnLongClickListener {
                onItemLongClick(item.packageName)
                true
            }
        }
    }
}
