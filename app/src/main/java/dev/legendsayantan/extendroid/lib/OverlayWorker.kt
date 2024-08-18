package dev.legendsayantan.extendroid.lib

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.card.MaterialCardView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.lib.Utils.Companion.dpToPx
import dev.legendsayantan.extendroid.lib.Utils.Companion.getAppIconFromPackage
import dev.legendsayantan.extendroid.lib.Utils.Companion.getForegroundColor
import dev.legendsayantan.extendroid.services.ExtendService
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.math.abs


/**
 * @author legendsayantan
 */
class OverlayWorker(val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val windows: HashMap<Int, View> = hashMapOf()
    private val renderers: HashMap<Int, View> = hashMapOf()
    private val windowSizes = hashMapOf<View, Pair<Int, Int>>()
    private val windowScales = hashMapOf<Int, Float>()
    private val mainMenuView: View by lazy { layoutInflater.inflate(R.layout.menu_control, null) }

    init {
        context.setTheme(R.style.Theme_Extendroid)
        ShizukuActions.execute("pm grant ${context.packageName} android.permission.SYSTEM_ALERT_WINDOW 0")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createWindow(
        id: Int,
        resolution: Pair<Int, Int>,
        pkg: String,
        mode: ExtendService.Companion.WindowMode,
        onSurfaceReady: (Surface) -> Unit,
        onTouch: (MotionEvent) -> Unit,
        onKeyEvent: (Int) -> Unit,
        onWindowClosed: () -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.app_popup, null)
        windowManager.addView(
            view, WindowManager.LayoutParams(
                resolution.first,
                resolution.second + context.dpToPx(16f),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                0
            ).apply {
                gravity = Gravity.END or Gravity.TOP
            }
        )
        println("saved id $id")
        windows[id] = view

        if (mode == ExtendService.Companion.WindowMode.POPUP) {
            renderers[id] = view.findViewById<SurfaceView>(R.id.surfaceView).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        this@apply.bringToFront()
                        onSurfaceReady(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        println("changed")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        //TODO("Not yet implemented")
                    }
                })
            }
        } else {
            renderers[id] = view.findViewById<TextureView>(R.id.textureView).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        onSurfaceReady(Surface(surface))
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        println("changed")
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        println("updated")
                    }
                }
            }
        }
        renderers[id]?.visibility = View.VISIBLE
        renderers[id]?.layoutParams = renderers[id]?.layoutParams?.apply {
            width = resolution.first
            height = resolution.second
        }

        //Touch handling
        renderers[id]?.setOnTouchListener { v, event ->
            onTouch(event)
            return@setOnTouchListener true
        }

        //menu actions
        val menu = view.findViewById<LinearLayout>(R.id.menu)
        val back = view.findViewById<MaterialCardView>(R.id.back)
        val downscale = view.findViewById<MaterialCardView>(R.id.downscale)
        val upscale = view.findViewById<MaterialCardView>(R.id.upscale)
        val close = view.findViewById<MaterialCardView>(R.id.close)
        val listOfMenu = listOf(back,downscale,upscale,close)
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
        //set app logo
        val imageView = view.findViewById<AppCompatImageView>(R.id.image)
        imageView.setImageDrawable(context.getAppIconFromPackage(pkg))
        imageView.visibility = View.GONE

        //make window movable, open menu
        var menuTimer = Timer()
        val taskMenuClose = {
            Handler(context.mainLooper).post {
                listOfMenu.forEachIndexed { index, it ->
                    it.animate().scaleX(0f).scaleY(0f).setStartDelay(((1.5f - abs(1.5f - index)) * 100).toLong())
                        .setListener(object : AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}
                            override fun onAnimationEnd(animation: Animator) {
                                if (index == listOfMenu.size - 1) menu.visibility = View.GONE
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}
                        }).start()
                }
            }
        }
        val handle = view.findViewById<MaterialCardView>(R.id.handle)
        var x = 0
        var y = 0
        var movedX = 0
        var movedY = 0
        handle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                    movedX = 0
                    movedY = 0
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - x
                    val dy = event.rawY.toInt() - y
                    x = event.rawX.toInt()
                    y = event.rawY.toInt()
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x -= dx
                    params.y += dy
                    movedX -= dx
                    movedY += dy
                    windowManager.updateViewLayout(view, params)
                }

                MotionEvent.ACTION_UP -> {
                    if (!isMinimized(view)) {
                        menuTimer.cancel()
                        menuTimer.purge()
                        menuTimer = Timer()
                        menuTimer.schedule(timerTask { taskMenuClose() }, 2500)
                        menu.visibility = View.VISIBLE
                        listOfMenu.forEachIndexed { index, view ->
                            view.scaleX = 0f
                            view.scaleY = 0f
                            view.translationX = (index - 1.5f) * 35
                            view.animate().scaleX(1f).scaleY(1f)
                                .setStartDelay((abs(1.5f - index) * 100).toLong()).translationX(0f)
                                .setListener(object : AnimatorListener {
                                    override fun onAnimationStart(animation: Animator) {
                                        menu.invalidate()
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        menu.invalidate()
                                    }

                                    override fun onAnimationCancel(animation: Animator) {}
                                    override fun onAnimationRepeat(animation: Animator) {}
                                }).start()
                        }
                    } else if (movedX in -10..10 && movedY in -10..10) {
                        maximize(view)
                    }
                }
            }
            return@setOnTouchListener true
        }
    }

    fun scaleWindow(id: Int, pkg: String, up: Boolean): WindowManager.LayoutParams {
        val extraAreaPx = context.dpToPx(16f)
        val view = windows[id]!!
        val oldScale = windowScales[id] ?: 1.0f
        val newScale = oldScale + if (up) 0.15f else -0.15f
        val renderArea = renderers[id]
        val params = view.layoutParams as WindowManager.LayoutParams
        val updatedWidth = renderArea?.width?.times(newScale) ?: 0
        val updatedHeight = (renderArea?.height?.times(newScale))?.plus(extraAreaPx) ?: 0
        if (
            (updatedWidth.toInt() > displayMetrics.widthPixels
                    || updatedHeight.toInt() > displayMetrics.heightPixels) && up
        ) return params
        if (
            (updatedWidth.toInt() < context.dpToPx(120f)
                    || updatedHeight.toInt() < context.dpToPx(80f)) && !up
        ) {
            minimize(view, pkg)
            return params
        }
        val widthDiff = updatedWidth.toInt() - params.width
        val heightDiff = updatedHeight.toInt() - params.height
        params.x -= widthDiff / 2
        params.y -= heightDiff / 2
        params.width = updatedWidth.toInt()
        params.height = updatedHeight.toInt()
        renderArea?.animate()?.scaleX(newScale)?.scaleY(newScale)
            ?.setListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    if (up) {
                        windowManager.updateViewLayout(view, params)
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!up) {
                        windowManager.updateViewLayout(view, params)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            })
        windowScales[id] = newScale
        return params
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
        val containerView = view.findViewById<LinearLayout>(R.id.container)
        containerView.visibility = View.GONE
        view.findViewById<AppCompatImageView>(R.id.image).visibility = View.VISIBLE
        view.findViewById<MaterialCardView>(R.id.handle).translationY =
            context.dpToPx(10f).toFloat()
        windowManager.updateViewLayout(view, params.apply {
            width = context.dpToPx(35f)
            height = context.dpToPx(35f)
        })
    }

    private fun isMinimized(view: View): Boolean {
        val containerView = view.findViewById<LinearLayout>(R.id.container)
        return containerView.visibility == View.GONE
    }

    private fun maximize(view: View) {
        val containerView = view.findViewById<LinearLayout>(R.id.container)
        view.findViewById<AppCompatImageView>(R.id.image).visibility = View.GONE
        view.findViewById<MaterialCardView>(R.id.handle).translationY = 0f
        containerView.visibility = View.VISIBLE
        windowManager.updateViewLayout(view, view.layoutParams.apply {
            width = windowSizes[view]?.first ?: context.dpToPx(50f)
            height = windowSizes[view]?.second ?: context.dpToPx(50f)
        })
    }

    fun startStreaming(id: Int, udpServer: UdpServer): Int {
        return if (renderers[id] is TextureView) StreamHandler.start(
            id,
            renderers[id] as TextureView,
            udpServer = udpServer
        )
        else -1
    }

    fun stopStreaming(id: Int, udpServer: UdpServer) {
        StreamHandler.stop(id, udpServer)
    }


    @SuppressLint("ClickableViewAccessibility")
    fun showMenu(onAdd: () -> Unit, onLightOff: () -> Unit) {
        var dimValue = 0f
        var blurValue = 0f
        val makeParams : (ViewGroup.LayoutParams?,Float,Float)->WindowManager.LayoutParams = { pos,dim,blur->
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or (if(dim>0) WindowManager.LayoutParams.FLAG_DIM_BEHIND else 0)
                        or (if(blur>0) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
                0
            ).apply {
                (pos as WindowManager.LayoutParams?)?.let { x=it.x;y=it.y }
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                dimAmount = dim
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = blur.toInt() * 200
                }
            }
        }
        windowManager.addView(mainMenuView, makeParams(null,0f,0f))

        val handle = mainMenuView.findViewById<ImageView>(R.id.handle)
        val add = mainMenuView.findViewById<ImageView>(R.id.addBtn)
        val dim = mainMenuView.findViewById<ImageView>(R.id.dim)
        val blur = mainMenuView.findViewById<ImageView>(R.id.blur)
        val lightOff = mainMenuView.findViewById<ImageView>(R.id.lightOff)
        handle.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        add.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        dim.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
        blur.imageTintList = ColorStateList.valueOf(context.getForegroundColor())
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
                    val params = makeParams(mainMenuView.layoutParams,dimValue,blurValue)
                    params.x += dx
                    params.y += dy
                    windowManager.updateViewLayout(mainMenuView, params)
                }
            }
            return@setOnTouchListener true
        }
        add.setOnClickListener { onAdd() }
        dim.setOnClickListener {
            dimValue = when(dimValue){
                0f-> 0.3f
                0.3f-> 0.6f
                else -> 0f
            }
            windowManager.updateViewLayout(mainMenuView,makeParams(mainMenuView.layoutParams,dimValue,blurValue))
        }
        blur.setOnClickListener {
            blurValue = when(blurValue){
                0f->0.3f
                0.3f->0.6f
                else->0f
            }
            windowManager.updateViewLayout(mainMenuView,makeParams(mainMenuView.layoutParams,dimValue,blurValue))
        }
        lightOff.setOnClickListener { onLightOff() }
    }

    fun hideMenu() {
        try {
            windowManager.removeView(mainMenuView)
        } catch (_: Exception) {
        }
    }
}