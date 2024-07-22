package dev.legendsayantan.extendroid

import android.content.Context
import android.view.WindowManager

/**
 * @author legendsayantan
 */
class OverlayWorker(val context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    init{

    }
}