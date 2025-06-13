package dev.legendsayantan.extendroid.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import dev.legendsayantan.extendroid.R
import kotlin.math.abs

/**
 * @author legendsayantan
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBall(val ctx: Context) : FrameLayout(ctx) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(ctx.mainLooper)
    private val fadeDelay = 5000L // 5 seconds
    private val fadeRunnable = Runnable { this.animate().alpha(0.5f) }

    private val displayMetrics
        get() = ctx.resources.displayMetrics
    private val screenWidth
        get() = displayMetrics.widthPixels
    private val screenHeight
        get() = displayMetrics.heightPixels

    private var clickCallback: (() -> Unit)? = null

    private val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

    init {
        // Use themed context for Material components
        val themedCtx = ContextThemeWrapper(ctx, R.style.Base_Theme_Extendroid)
        LayoutInflater.from(themedCtx).inflate(R.layout.layout_bubble, this, true)
        setupDragAndClick()
    }

    private fun setupDragAndClick() {
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f

        setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(fadeRunnable)
                    animate().alpha(1f)
                    downX = ev.rawX
                    downY = ev.rawY
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - lastX).toInt()
                    val dy = (ev.rawY - lastY).toInt()
                    layoutParams.apply {
                        x = (x + dx).coerceIn(0, screenWidth - width)
                        y = (y + dy).coerceIn(0, screenHeight - height)
                    }
                    wm.updateViewLayout(this, layoutParams)
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // compute target X
                    val startX = layoutParams.x
                    val targetX =
                        if (startX + width / 2 < screenWidth / 2) 0 else screenWidth - width

                    ValueAnimator.ofInt(startX, targetX).apply {
                        duration = 250L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            layoutParams.x = anim.animatedValue as Int
                            try {
                                wm.updateViewLayout(this@FloatingBall, layoutParams)
                            } catch (e: Exception) {
                                cancel()
                            }
                        }
                    }.start()


                    // detect tap
                    if (abs(downX - lastX) < 10 && abs(downY - lastY) < 10) {
                        clickCallback?.invoke()
                    }

                    // schedule fade
                    handler.postDelayed(fadeRunnable, fadeDelay)
                    true
                }

                else -> false
            }
        }
    }

    fun setOnClickListener(block: () -> Unit) {
        clickCallback = block
    }

    fun show() {
        if (parent == null) {
            wm.addView(this, layoutParams)
            handler.postDelayed(fadeRunnable, fadeDelay)
        }
    }

    fun hide() {
        if (parent != null) {
            wm.removeView(this)
            handler.removeCallbacks(fadeRunnable)
        }
    }

}


