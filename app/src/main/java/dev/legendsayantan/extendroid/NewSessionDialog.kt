package dev.legendsayantan.extendroid

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import dev.legendsayantan.extendroid.adapters.RecyclerGridAdapter
import dev.legendsayantan.extendroid.lib.Utils.Companion.getLaunchableApps
import dev.legendsayantan.extendroid.services.ExtendService

/**
 * @author legendsayantan
 */
class NewSessionDialog(
    context: Context,
    val onSelectionDone: (String, Pair<Int, Int>, Int, Boolean, ExtendService.Companion.WindowMode) -> Unit
) : Dialog(context) {
    var quality = 480
    var aspect = 1f
    var autoDensity = true
    var density = DisplayMetrics.DENSITY_DEFAULT

    init {
        context.setTheme(R.style.Theme_Extendroid)
        setContentView(R.layout.dialog_new_session)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun show() {
        super.show()
        val qualities = linkedMapOf(
            Pair(R.id.q_480, 480),
            Pair(R.id.q_hd, 720),
            Pair(R.id.q_2k, 1080),
            Pair(R.id.q_4k, 2160)
        )
        val aspects = linkedMapOf(
            Pair(R.id.a_16_9, 16f / 9f),
            Pair(R.id.a_3_2, 3f / 2f),
            Pair(R.id.a_1_1, 1f),
            Pair(R.id.a_2_3, 2f / 3f),
            Pair(R.id.a_9_16, 9f / 16f)
        )
        val densities = arrayOf(
            DisplayMetrics.DENSITY_LOW.toString(),
            DisplayMetrics.DENSITY_MEDIUM.toString(),
            ((DisplayMetrics.DENSITY_MEDIUM+DisplayMetrics.DENSITY_HIGH)/2).toString(),
            DisplayMetrics.DENSITY_HIGH.toString(),
            DisplayMetrics.DENSITY_XHIGH.toString(),
            DisplayMetrics.DENSITY_XXHIGH.toString(),
            DisplayMetrics.DENSITY_XXXHIGH.toString(),
        )

        val qualityToDensityIndex : () -> Float = {
            ((densities.size - qualities.size) + qualities.values.indexOf(quality).toFloat())
        }
        val startHelper = findViewById<MaterialSwitch>(R.id.startHelper)
        val densityPicker = findViewById<Slider>(R.id.densityPicker)
        val modes = hashMapOf(
            Pair(R.id.popup, ExtendService.Companion.WindowMode.POPUP),
            Pair(R.id.wireless, ExtendService.Companion.WindowMode.WIRELESS)
        )
        val appsView = findViewById<RecyclerView>(R.id.apps)
        if (appsView != null) {
            appsView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 4)
            Thread {
                val apps = context.getLaunchableApps()
                Handler(context.mainLooper).post {
                    appsView.adapter = RecyclerGridAdapter(context, apps) { selectedPkg ->
                        if (modes.none { findViewById<MaterialCardView>(it.key).strokeWidth > 0 }) return@RecyclerGridAdapter
                        dismiss()
                        val mode =
                            if (modes.all { findViewById<MaterialCardView>(it.key).strokeWidth > 0 }) ExtendService.Companion.WindowMode.BOTH
                            else modes.entries.first { findViewById<MaterialCardView>(it.key).strokeWidth > 0 }.value

                        onSelectionDone(
                            selectedPkg,
                            calculateWidthAndHeight(),
                            (density + context.resources.displayMetrics.densityDpi)/2,
                            startHelper.isChecked,
                            mode
                        )
                    }
                }
            }.start()
        }

        qualities.entries.forEachIndexed { index, mutableEntry ->
            findViewById<ImageView>(mutableEntry.key).setOnClickListener { view ->
                quality = mutableEntry.value
                qualities.entries.forEach {
                    findViewById<ImageView>(it.key).alpha = 1f
                }
                view.alpha = 0.5f
                if(autoDensity){
                    densityPicker.value = qualityToDensityIndex()
                }
            }
        }

        aspects.entries.forEachIndexed { index, mutableEntry ->
            findViewById<LinearLayout>(mutableEntry.key).setOnClickListener { view ->
                aspect = mutableEntry.value
                aspects.entries.forEach {
                    findViewById<LinearLayout>(it.key).alpha = 1f
                }
                view.alpha = 0.5f
            }
        }

        densityPicker.value = qualityToDensityIndex()
        densityPicker.valueFrom = 0f
        densityPicker.valueTo = densities.size-1f
        densityPicker.stepSize = 1f
        densityPicker.setLabelFormatter { densities[it.toInt()]+" dpi" }
        densityPicker.addOnChangeListener { _, value, fromUser ->
            if(fromUser) autoDensity = false
            density = densities[value.toInt()].toInt()
        }

        modes.forEach {
            findViewById<MaterialCardView>(it.key).apply {
                setOnClickListener { strokeWidth = if (strokeWidth > 0) 0 else 5 }
            }
        }
    }

    fun calculateWidthAndHeight(): Pair<Int, Int> {
        return if (aspect > 1f) {
            Pair((quality * aspect).toInt(), quality)
        } else {
            Pair(quality, (quality / aspect).toInt())
        }
    }
}