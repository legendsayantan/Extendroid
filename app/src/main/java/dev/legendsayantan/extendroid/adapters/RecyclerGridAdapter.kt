package dev.legendsayantan.extendroid.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.R

/**
 * @author legendsayantan
 */
class RecyclerGridAdapter(
    context: Context,
    private var apps: HashMap<String, String>,
    val onClick : (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerGridAdapter.AppViewHolder>() {


    val packageManager = context.packageManager
    val keysArray by lazy { apps.keys.toTypedArray() }

    init {
        apps = apps.entries.sortedBy { it.value.lowercase() }.associateBy({ it.key }, { it.value }) as HashMap<String, String>
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val pkg = keysArray[position]
        val app = apps[pkg]
        holder.appName.text = app
        holder.appIcon.setImageDrawable(packageManager.getApplicationIcon(pkg))
        holder.itemView.setOnClickListener {
            onClick(pkg)
        }
    }

    override fun getItemCount() = apps.size

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
    }
}
