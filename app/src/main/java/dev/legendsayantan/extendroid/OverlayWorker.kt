package dev.legendsayantan.extendroid

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import dev.legendsayantan.extendroid.Utils.Companion.dpToPx
import dev.legendsayantan.extendroid.Utils.Companion.getAppIconFromPackage
import dev.legendsayantan.extendroid.Utils.Companion.getForegroundColor
import java.util.Timer
import kotlin.concurrent.timerTask


/**
 * @author legendsayantan
 */
class OverlayWorker(val context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val displayMetrics = context.resources.displayMetrics
    val layoutInflater = LayoutInflater.from(context)
    val windows: HashMap<Int, View> = hashMapOf()
    val windowSizes = hashMapOf<View, Pair<Int, Int>>()
    val windowScales = hashMapOf<Int, Float>()
    val menuView by lazy { layoutInflater.inflate(R.layout.menu_control, null) }

    init {
        context.setTheme(R.style.Theme_Extendroid)
        ShizukuActions.execute("pm grant ${context.packageName} android.permission.SYSTEM_ALERT_WINDOW 0")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createWindow(
        id: Int,
        resolution: Pair<Int, Int>,
        pkg: String,
        onSurfaceReady: (Surface) -> Unit,
        onTouch: (MotionEvent) -> Unit,
        onKeyEvent: (Int) -> Unit,
        onWindowClosed: () -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.app_popup, null)
        windowManager.addView(
            view, WindowManager.LayoutParams(
                resolution.first,
                resolution.second + context.dpToPx(15f),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                0
            ).apply {
                gravity = Gravity.END or Gravity.TOP
            }
        )
        println("saved id $id")
        windows[id] = view

        val surfaceView = view.findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceView.bringToFront()
                onSurfaceReady(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                //TODO("Not yet implemented")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                //TODO("Not yet implemented")
            }
        })
        surfaceView.layoutParams = surfaceView.layoutParams.apply {
            width = resolution.first
            height = resolution.second
        }

        //Touch handling
        surfaceView.setOnTouchListener { v, event ->
            onTouch(event)
            return@setOnTouchListener true
        }

        //menu actions
        val menu = view.findViewById<ConstraintLayout>(R.id.menu)
        val back = view.findViewById<ImageView>(R.id.back)
        val downscale = view.findViewById<ImageView>(R.id.downscale)
        val upscale = view.findViewById<ImageView>(R.id.upscale)
        val close = view.findViewById<ImageView>(R.id.close)

        back.setOnClickListener {
            onKeyEvent(KeyEvent.KEYCODE_BACK)
        }
        downscale.setOnClickListener {
            scaleWindow(id, pkg, false)
        }
        upscale.setOnClickListener {
            scaleWindow(id, pkg, true)
        }
        close.setOnClickListener {
            deleteWindow(id)
            onWindowClosed()
        }

        //make window movable, open menu
        var menuTimer = Timer()
        val taskMenuClose = {
            Handler(context.mainLooper).post {
                menu.animate().alpha(0f).scaleX(0f).scaleY(0f)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}
                        override fun onAnimationEnd(animation: Animator) {
                            menu.visibility = View.GONE
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}
                    }).start()
            }
        }
        val handle = view.findViewById<AppCompatImageView>(R.id.handle)
        var x = 0
        var y = 0
        handle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - x
                    val dy = event.rawY.toInt() - y
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x -= dx
                    params.y += dy
                    windowManager.updateViewLayout(view, params)
                }

                MotionEvent.ACTION_UP -> {
                    if (isMinimized(view)) {
                        maximize(view)
                    }
                    menuTimer.cancel()
                    menuTimer.purge()
                    menuTimer = Timer()
                    menuTimer.schedule(timerTask { taskMenuClose() }, 2500)
                    if (menu.visibility == View.GONE) {
                        menu.alpha = 0f
                        menu.scaleX = 0f
                        menu.scaleY = 0f
                        menu.visibility = View.VISIBLE
                    }
                    menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setListener(null).start()
                }
            }
            return@setOnTouchListener true
        }
    }

    private fun scaleWindow(id: Int, pkg: String, up: Boolean) {
        val extraAreaPx = context.dpToPx(15f)
        val view = windows[id]!!
        val oldScale = windowScales[id] ?: 1.0f
        val newScale = oldScale + if (up) 0.15f else -0.15f
        val surfaceView = view.findViewById<SurfaceView>(R.id.surfaceView)
        val params = view.layoutParams as WindowManager.LayoutParams
        println(params.height)
        val updatedWidth = surfaceView.width * newScale
        val updatedHeight = (surfaceView.height * newScale) + extraAreaPx
        if (
            (updatedWidth.toInt() > displayMetrics.widthPixels
                    || updatedHeight.toInt() > displayMetrics.heightPixels) && up
        ) return
        if (
            (updatedWidth.toInt() < context.dpToPx(120f)
                    || updatedHeight.toInt() < context.dpToPx(80f)) && !up
        ) {
            minimize(view, pkg)
            return
        }
        params.width = updatedWidth.toInt()
        params.height = updatedHeight.toInt()
        windowManager.updateViewLayout(view, params)
        surfaceView.scaleX = newScale
        surfaceView.scaleY = newScale
        windowScales[id] = newScale
    }

    fun deleteWindow(id: Int) {
        println("removing id $id")
        try {
            windowManager.removeView(windows[id])
        } catch (_: Exception) {

        } finally {
            windows.remove(id)
        }
    }

    private fun minimize(view: View, pkg: String) {
        val params = view.layoutParams
        windowSizes[view] = Pair(params.width, params.height)
        val containerView = view.findViewById<CardView>(R.id.container)
        containerView.visibility = View.GONE
        val imageView = view.findViewById<AppCompatImageView>(R.id.image)
        imageView.setImageDrawable(context.getAppIconFromPackage(pkg))
        imageView.imageTintList = null
        imageView.rotation = 0f
        windowManager.updateViewLayout(view, params.apply {
            width = context.dpToPx(50f)
            height = context.dpToPx(50f)
        })
    }

    private fun isMinimized(view: View): Boolean {
        val containerView = view.findViewById<CardView>(R.id.container)
        return containerView.visibility == View.GONE
    }

    private fun maximize(view: View) {
        val containerView = view.findViewById<CardView>(R.id.container)
        containerView.visibility = View.VISIBLE
        val imageView = view.findViewById<AppCompatImageView>(R.id.image)
        imageView.setImageResource(R.drawable.baseline_drag_indicator_24)
        imageView.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        imageView.rotation = 90f
        windowManager.updateViewLayout(view, view.layoutParams.apply {
            width = windowSizes[view]?.first ?: context.dpToPx(50f)
            height = windowSizes[view]?.second ?: context.dpToPx(50f)
        })
    }


    @SuppressLint("ClickableViewAccessibility")
    fun showMenu(onAdd: () -> Unit, onLightOff: () -> Unit) {
        windowManager.addView(menuView, WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            0
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        })

        val handle = menuView.findViewById<ImageView>(R.id.handle)
        val add = menuView.findViewById<ImageView>(R.id.addBtn)
        val lightOff = menuView.findViewById<ImageView>(R.id.lightOff)
        handle.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        add.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        lightOff.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        var x = 0
        var y = 0
        handle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - x
                    val dy = event.rawY.toInt() - y
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                    val params = menuView.layoutParams as WindowManager.LayoutParams
                    params.x += dx
                    params.y += dy
                    windowManager.updateViewLayout(menuView, params)
                }
            }
            return@setOnTouchListener true
        }
        add.setOnClickListener { onAdd() }
        lightOff.setOnClickListener { onLightOff() }

    }

    fun hideMenu() {
       try {
           windowManager.removeView(menuView)
       }catch (_:Exception){}
    }
}