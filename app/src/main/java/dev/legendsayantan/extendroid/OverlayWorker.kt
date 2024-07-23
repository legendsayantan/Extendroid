package dev.legendsayantan.extendroid

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.constraintlayout.widget.ConstraintLayout
import dev.legendsayantan.extendroid.Utils.Companion.dpToPx


/**
 * @author legendsayantan
 */
class OverlayWorker(val context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val displayMetrics = context.resources.displayMetrics
    val layoutInflater = LayoutInflater.from(context)
    val windows: HashMap<Int, View> = hashMapOf()
    val windowScales = hashMapOf<Int, Float>()

    init {
        ShizukuActions.execute("pm grant ${context.packageName} android.permission.SYSTEM_ALERT_WINDOW 0")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createWindow(
        id: Int,
        resolution: Pair<Int, Int>,
        onSurfaceReady: (Surface) -> Unit,
        onTouch: (MotionEvent) -> Unit,
        onKeyEvent: (Int)->Unit,
        onWindowClosed:()->Unit
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
            width=resolution.first
            height=resolution.second
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
            scaleWindow(id, false)
        }
        upscale.setOnClickListener {
            scaleWindow(id, true)
        }
        close.setOnClickListener {
            deleteWindow(id)
            onWindowClosed()
        }

        //make window movable, open menu
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
                    menu.alpha = 0f
                    menu.scaleX = 0f
                    menu.scaleY = 0f
                    menu.visibility = View.VISIBLE
                    menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setListener(null).start()
                    Handler(context.mainLooper).postDelayed({
                        menu.animate().alpha(0f).scaleX(0f).scaleY(0f)
                            .setListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}
                                override fun onAnimationEnd(animation: Animator) {
                                    menu.visibility = View.GONE
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}
                            })
                    }, 5000)
                }
            }
            return@setOnTouchListener true
        }
    }

    private fun scaleWindow(id: Int, up: Boolean) {
        val extraAreaPx = context.dpToPx(15f)
        val view = windows[id]!!
        val oldScale = windowScales[id] ?: 1.0f
        val newScale = oldScale + if (up) 0.1f else -0.1f
        val surfaceView = view.findViewById<SurfaceView>(R.id.surfaceView)
        val params = view.layoutParams as WindowManager.LayoutParams
        println(params.height)
        val updatedWidth = surfaceView.width * newScale
        val updatedHeight = (surfaceView.height * newScale) + extraAreaPx
        if (
            updatedWidth.toInt() !in 200..displayMetrics.widthPixels
            || updatedHeight.toInt() !in 150..displayMetrics.heightPixels
        ) return
        params.width = updatedWidth.toInt()
        params.height = updatedHeight.toInt()
        windowManager.updateViewLayout(view, params)
        surfaceView.scaleX = newScale
        surfaceView.scaleY = newScale
        windowScales[id] = newScale
    }

    private fun deleteWindow(id: Int) {
        windowManager.removeView(windows[id])
        windows.remove(id)
    }
}