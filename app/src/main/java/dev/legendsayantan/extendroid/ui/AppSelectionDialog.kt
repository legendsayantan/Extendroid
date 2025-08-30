package dev.legendsayantan.extendroid.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.legendsayantan.extendroid.adapters.AppGridAdapter
import dev.legendsayantan.extendroid.model.AppItem
import dev.legendsayantan.extendroid.R;

class AppSelectionDialog(
    themedCtx: Context,
    private val allApps: List<AppItem>,
    private val selectedApps: Set<String>,
    private val title: String = "Select apps",
    private val onSelectionDone: (Set<String>) -> Unit
) {
    val allPackages = allApps.map { it.packageName }
    val selectedSet = selectedApps.toMutableSet()
    lateinit var dialog: AlertDialog

    init{
        val layoutInflater = LayoutInflater.from(themedCtx)
        val view = layoutInflater.inflate(R.layout.dialog_app_selection,null)
        dialog = MaterialAlertDialogBuilder(themedCtx)
            .setView(view)
            .create()
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val heading = view.findViewById<TextView>(R.id.heading)
        heading.text = title
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2) // 2-column grid
            adapter = AppGridAdapter(allApps, { pkg ->
                if (selectedSet.contains(pkg)) {
                    selectedSet.remove(pkg)
                } else {
                    selectedSet.add(pkg)
                }
                adapter?.notifyDataSetChanged()
            }).apply { preselected = selectedSet }
        }
        val btnSelectAll = view.findViewById<MaterialCardView>(R.id.btnSelectAll)
        btnSelectAll.setOnClickListener {
            if((allPackages - selectedSet).isNotEmpty()){
                selectedSet.clear()
                selectedSet.addAll(allApps.map { it.packageName })
            }else{
                selectedSet.clear()
            }
            recyclerView.adapter?.notifyDataSetChanged()
        }
        val doneBtn = view.findViewById<MaterialButton>(R.id.doneBtn)
        doneBtn.setOnClickListener {
            onSelectionDone(selectedSet)
            dialog.dismiss()
        }
    }

    fun show() {
        dialog.show()
    }
}
