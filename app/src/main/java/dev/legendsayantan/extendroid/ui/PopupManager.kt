package dev.legendsayantan.extendroid.ui

import android.content.Context
import android.view.MotionEvent
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.lib.MediaCore

/**
 * @author legendsayantan
 */
class PopupManager(val context: Context) {

    private val prefs by lazy { Prefs(context) }
    private val popupWindows = hashMapOf<String, PopupWindow>()

    var onPopupMinimize: (String, Float) -> Unit = { pkg, ratio -> }
    var onKeyEvent: (String, Int, Int) -> Unit = { pkg, kCode, action -> }
    var onMotionEvent: (String, MotionEvent) -> Unit = { pkg, event -> }

    fun getSpawnLocation(): Pair<Int, Int> {
        return popupWindows.entries.lastOrNull()?.value?.positionParams?.let {
            val metrics = context.resources.displayMetrics
            Pair(
                it.x + (if ((metrics.widthPixels / 2) > (it.x + (it.width / 2))) 50 else -50),
                it.y + (if ((metrics.heightPixels / 2) > (it.y + (it.height / 2))) 50 else -50)
            )
        } ?: Pair(0, 100)
    }

    fun createNew(packageName: String, w: Int, h: Int, color: Int) {
        val newPopup = PopupWindow(
            context, w, h, getSpawnLocation(), color,
            if (popupWindows.isEmpty()) prefs.backgroundDim else 0f
        )
        newPopup.onSurfaceAvailable = { s ->
            MediaCore.mInstance?.updateSurface(packageName, s)
        }
        newPopup.onSurfaceChanged = { s, w, h ->
            MediaCore.mInstance?.setupVirtualDisplay(context, packageName, s, w, h)
        }
        newPopup.onKeyEventCallback = { keyCode, action ->
            onKeyEvent(packageName, keyCode, action)
        }
        newPopup.onTouchEventCallback = { onMotionEvent(packageName, it) }
        newPopup.onDeleteRequest = { deletePopup(packageName, it) }
        popupWindows[packageName] = newPopup.show()
    }

    fun hasPopup(packageName: String): Boolean {
        return popupWindows.containsKey(packageName)
    }

    fun deletePopup(packageName: String, reason: PopupDeleteReason) {
        popupWindows[packageName]?.let {
            it.remove()
            when (reason) {
                PopupDeleteReason.MINIMIZE -> onPopupMinimize(
                    packageName,
                    it.layoutParams.let { it.height.toFloat() / it.width })

                PopupDeleteReason.MAXIMIZE -> MediaCore.mInstance?.fullScreen(packageName)
                PopupDeleteReason.TERMINATE -> MediaCore.mInstance?.stopVirtualDisplay(packageName)
            }
        }
        popupWindows.remove(packageName)
    }

    enum class PopupDeleteReason {
        MINIMIZE,
        MAXIMIZE,
        TERMINATE
    }

}