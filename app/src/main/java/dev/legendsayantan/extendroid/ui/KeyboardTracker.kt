package dev.legendsayantan.extendroid.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.widget.FrameLayout

class KeyboardTracker(private val context: Context, private val onKeyboardHeightChanged: (Int) -> Unit) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val trackerLayout = FrameLayout(context)

    private val layoutParams = WindowManager.LayoutParams().apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        format = PixelFormat.TRANSLUCENT
    }

    private var lastKeyboardHeight = -1

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val rect = Rect()
        trackerLayout.getWindowVisibleDisplayFrame(rect)
        val screenHeight = trackerLayout.rootView.height
        
        var keyboardHeight = screenHeight - rect.bottom
        if (keyboardHeight < screenHeight * 0.15) {
            keyboardHeight = 0
        }
        
        if (keyboardHeight != lastKeyboardHeight) {
            lastKeyboardHeight = keyboardHeight
            onKeyboardHeightChanged(keyboardHeight)
        }
    }

    fun start() {
        trackerLayout.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        wm.addView(trackerLayout, layoutParams)
    }

    fun stop() {
        trackerLayout.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        try {
            if (trackerLayout.windowToken != null) {
                wm.removeView(trackerLayout)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
