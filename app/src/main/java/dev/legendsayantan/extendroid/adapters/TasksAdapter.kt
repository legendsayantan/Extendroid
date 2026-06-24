package dev.legendsayantan.extendroid.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.model.TaskData
import dev.legendsayantan.extendroid.Utils

class TasksAdapter(
    private val ctx: Context,
    private val tasks: MutableList<TaskData>,
    private val appIcons: Map<String, Drawable>,
    private val appNames: Map<String, String>,
    private val onRun: (TaskData) -> Unit,
    private val onDelete: (TaskData) -> Unit,
    private val onEdit: (TaskData) -> Unit
) : RecyclerView.Adapter<TasksAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(ctx).inflate(R.layout.layout_item_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskName.text = task.taskKey
        holder.taskPkg.text = appNames[task.pkgName] ?: task.pkgName
        appIcons[task.pkgName]?.let {
            holder.taskIcon.setImageDrawable(it)
            holder.taskCard.strokeColor = Utils.getColorOf(it)
        } ?: run {
            holder.taskIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.taskCard.strokeColor = Color.GRAY
        }

        holder.runBtn.setOnClickListener { onRun(task) }
        holder.moreBtn.setOnClickListener { view ->
            val popup = PopupMenu(ctx, view)
            popup.menu.add(0, 1, 0, R.string.edit_task)
            popup.menu.add(0, 2, 0, R.string.delete_task)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showEditDialog(task, position)
                    2 -> {
                        onDelete(task)
                        tasks.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, tasks.size)
                    }
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = tasks.size

    private fun showEditDialog(task: TaskData, position: Int) {
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.layout_dialog_edit_task, null)
        val slider = dialogView.findViewById<Slider>(R.id.delaySlider)
        val stopSwitch = dialogView.findViewById<MaterialSwitch>(R.id.stopSwitch)
        
        slider.value = (task.launchDelayMs / 1000f).coerceIn(0.5f, 10f)
        stopSwitch.isChecked = task.stopAfter

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<MaterialButton>(R.id.saveBtn).setOnClickListener {
            val updatedTask = task.copy(
                launchDelayMs = (slider.value * 1000).toLong(),
                stopAfter = stopSwitch.isChecked
            )
            tasks[position] = updatedTask
            notifyItemChanged(position)
            onEdit(updatedTask)
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.cancelBtn).setOnClickListener {
            dialog.dismiss()
        }

        var isActivity = false
        var unwrappedCtx = ctx
        while (unwrappedCtx is android.content.ContextWrapper) {
            if (unwrappedCtx is android.app.Activity) {
                isActivity = true
                break
            }
            unwrappedCtx = unwrappedCtx.baseContext
        }
        if (!isActivity) {
            dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskCard: MaterialCardView = view.findViewById(R.id.taskCard)
        val taskIcon: ImageView = view.findViewById(R.id.taskIcon)
        val taskName: TextView = view.findViewById(R.id.taskName)
        val taskPkg: TextView = view.findViewById(R.id.taskPkg)
        val runBtn: MaterialButton = view.findViewById(R.id.runBtn)
        val moreBtn: ImageButton = view.findViewById(R.id.moreBtn)
    }
}
