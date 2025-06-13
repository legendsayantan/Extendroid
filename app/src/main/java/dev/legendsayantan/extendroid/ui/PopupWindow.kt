package dev.legendsayantan.extendroid.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.card.MaterialCardView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils.Companion.dpToPx
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.get
import dev.legendsayantan.extendroid.Utils
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
@SuppressLint("ClickableViewAccessibility")
class PopupWindow(
    val ctx: Context,
    val widthPixels: Int,
    val heightPixels: Int,
    val spawnLocation: Pair<Int, Int>,
    val color: Int,
    val dimValue: Float = 0f
) : FrameLayout(ctx) {

    var onSurfaceAvailable: (Surface) -> Unit = {}
    var onSurfaceChanged: (Surface, Int, Int) -> Unit = { s, w, h -> }

    var onDeleteRequest: (PopupManager.PopupDeleteReason) -> Unit = {}
    var onKeyEventCallback: (Int, Int) -> Unit = { kCode, action -> }
    var onTouchEventCallback: (MotionEvent) -> Unit = { }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val positionParams = WindowManager.LayoutParams().apply {
        val originalRatio = widthPixels.toFloat()/heightPixels
        val optimised = optimiseSizes(ctx, widthPixels, heightPixels)
        val newRatio = optimised.first.toFloat()/optimised.second
        println(originalRatio)
        println(newRatio)
        width = optimised.first
        height = optimised.second
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                if(dimValue!=0f) WindowManager.LayoutParams.FLAG_DIM_BEHIND else 0
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        dimAmount = dimValue
        x = spawnLocation.first
        y = spawnLocation.second
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop

    var resizeInitialWidth = 0
    var resizeInitialHeight = 0
    var resizeInitialX = 0
    var resizeInitialY = 0
    var resizeTouchStartX = 0f
    var resizeTouchStartY = 0f

    var controlTimer = Timer()

    val themedCtx by lazy { ContextThemeWrapper(ctx, R.style.Base_Theme_Extendroid) }
    val root by lazy { LayoutInflater.from(themedCtx).inflate(R.layout.layout_app_window, this, true) }
    val surfaceParent by lazy { root.findViewById<LinearLayout>(R.id.surfaceParent) }
    val dragHandle by lazy { root.findViewById<LinearLayout>(R.id.handle) }
    val handleCard by lazy { root.findViewById<MaterialCardView>(R.id.handleCard) }
    val surfaceContainer by lazy { root.findViewById<SurfaceView>(R.id.surfaceView) }
    val leftResizer by lazy { root.findViewById<LinearLayout>(R.id.leftResizer) }
    val rightResizer by lazy { root.findViewById<LinearLayout>(R.id.rightResizer) }

    val controls by lazy { root.findViewById<LinearLayout>(R.id.controls) }
    val hideBtn by lazy { root.findViewById<MaterialCardView>(R.id.hideBtn) }
    val fullBtn by lazy { root.findViewById<MaterialCardView>(R.id.fullBtn) }
    val backBtn by lazy { root.findViewById<MaterialCardView>(R.id.backBtn) }
    val closeBtn by lazy { root.findViewById<MaterialCardView>(R.id.closeBtn) }

    init {
        surfaceParent.background = color.toDrawable()
        handleCard.setCardBackgroundColor(ColorStateList.valueOf(color))
        val colors = arrayOf(
            Utils.mixColors(color, resources.getColor(R.color.white,null), 0.5f),
            Utils.mixColors(color, resources.getColor(R.color.black,null), 0.5f)
        )
        if(Utils.isDarkModeEnabled(ctx)) colors.reverse()
        arrayOf(
            hideBtn,
            fullBtn,
            backBtn,
            closeBtn
        ).forEach {
            it.setCardBackgroundColor(colors[0])
            it.strokeColor = colors[1]
            (it[0] as AppCompatImageView).imageTintList = ColorStateList.valueOf(colors[1])
        }
        if (surfaceContainer is SurfaceView) {
            surfaceContainer.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    onSurfaceAvailable(holder.surface)
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    onSurfaceChanged(holder.surface, width, height)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {}

            })
        }

        surfaceContainer.setOnTouchListener { view, event ->
            onTouchEventCallback(event)
            true
        }

        // Drag & Click handling on dragHandle
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Record initial positions
                    initialX = positionParams.x
                    initialY = positionParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        // Update position
                        positionParams.x = initialX + dx
                        positionParams.y = initialY + dy
                        wm.updateViewLayout(this, positionParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Treat as click: toggle controls
                        if (controls.translationY == 0f) {
                            hideControls()
                        } else {
                            showControls()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        // LEFT resizer (bottom‑left corner)
        // LEFT resizer (bottom‑left corner)
        leftResizer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeInitialWidth = positionParams.width
                    resizeInitialHeight = positionParams.height
                    resizeInitialX = positionParams.x
                    resizeTouchStartX = event.rawX
                    resizeTouchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - resizeTouchStartX).toInt()
                    val dy = (event.rawY - resizeTouchStartY).toInt()

                    // Compute new raw size
                    val rawWidth = resizeInitialWidth - dx
                    val rawHeight = resizeInitialHeight + dy

                    // Clamp to minimums
                    val (newW, newH) = coerceSizes(context,rawWidth, rawHeight)

                    // Here’s the only change: fix the right edge at its original X + width
                    val initialRightX = resizeInitialX + resizeInitialWidth
                    positionParams.x = initialRightX - newW

                    positionParams.width = newW
                    positionParams.height = newH

                    wm.updateViewLayout(this, positionParams)
                    true
                }

                else -> false
            }
        }


// RIGHT resizer (bottom‑right corner)
        rightResizer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeInitialWidth = positionParams.width
                    resizeInitialHeight = positionParams.height
                    resizeTouchStartX = event.rawX
                    resizeTouchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - resizeTouchStartX).toInt()
                    val dy = (event.rawY - resizeTouchStartY).toInt()

                    // dragging right handle: dx > 0 expands right, dx < 0 shrinks
                    val rawWidth = resizeInitialWidth + dx
                    val rawHeight = resizeInitialHeight + dy

                    val (newW, newH) = coerceSizes(context,rawWidth, rawHeight)

                    positionParams.width = newW
                    positionParams.height = newH
                    wm.updateViewLayout(this, positionParams)
                    true
                }

                else -> false
            }
        }


        controls.visibility = GONE

        hideBtn.setOnClickListener { onDeleteRequest(PopupManager.PopupDeleteReason.MINIMIZE) }
        fullBtn.setOnClickListener { onDeleteRequest(PopupManager.PopupDeleteReason.MAXIMIZE) }
        backBtn.setOnTouchListener { view, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> onKeyEventCallback(
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.ACTION_DOWN
                )

                MotionEvent.ACTION_UP -> onKeyEventCallback(
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.ACTION_UP
                )
            }
            true
        }
        closeBtn.setOnClickListener { onDeleteRequest(PopupManager.PopupDeleteReason.TERMINATE) }
    }

    fun showControls() {
        controls.translationY = ctx.dpToPx(-100f)
        controls.visibility = VISIBLE
        controls.animate().translationY(0f).alpha(1f)
        try {
            controlTimer.cancel()
        } catch (_: Exception) {
        }
        controlTimer = Timer()
        controlTimer.schedule(timerTask {
            handler.post { hideControls() }
        }, 3000)
    }

    fun hideControls() {
        try {
            controls.animate().translationY(ctx.dpToPx(-100f)).alpha(0f)
            handler.postDelayed({
                controls.visibility = GONE
            }, 250)
        } catch (_: Exception) {
        }
    }

    fun show(): PopupWindow {
        if (parent == null) {
            wm.addView(this, positionParams)
        }
        return this
    }

    fun remove() {
        if (parent != null) {
            controlTimer.cancel()
            wm.removeView(this)
        }
    }

    companion object {

        fun getLimit(context: Context): Float{
            return context.dpToPx(175f)
        }
        // Helper to clamp to your minimums
        fun coerceSizes(context: Context,width: Int, height: Int): Pair<Int, Int> {
            val w = width.coerceAtLeast(getLimit(context).toInt())
            val h = height.coerceAtLeast(getLimit(context).toInt())
            return w to h
        }

        //keeping the aspect ratio same and ensuring neither width nor height is under limit
        fun optimiseSizes(context: Context,width:Int,height: Int): Pair<Int, Int>{
            val limit = getLimit(context)
            if (width >= limit && height >= limit) {
                return width to height
            }

            val scaleW = limit  / width.toFloat()
            val scaleH = limit / height.toFloat()

            val scale = maxOf(scaleW, scaleH)

            val newW = kotlin.math.ceil(width  * scale).toInt()
            val newH = kotlin.math.ceil(height * scale).toInt()
            return newW to newH
        }
    }
}