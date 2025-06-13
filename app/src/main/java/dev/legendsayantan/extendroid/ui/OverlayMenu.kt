package dev.legendsayantan.extendroid.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.tabs.TabLayout
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.Utils.Companion.dpToPx
import dev.legendsayantan.extendroid.adapters.AppGridAdapter
import dev.legendsayantan.extendroid.adapters.StaggeredGridAdapter
import dev.legendsayantan.extendroid.model.AppItem
import dev.legendsayantan.extendroid.model.WindowData
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.lib.PackageManagerHelper
import androidx.core.graphics.drawable.toDrawable


/**
 * @author legendsayantan
 */
class OverlayMenu(val ctx: Context) : FrameLayout(ctx) {
    var openInPopup: (String, Int, Int, Int) -> Unit = { s, w, h, c -> }
    var containsPopup: (String) -> Boolean = { false }
    var minimisePopup: (String) -> Unit = {}
    var getTopApps: () -> List<String> = { listOf() }
    var requestDisableScreen: () -> Unit = {}
    var requestStartSelf: ()-> Unit = {}

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val displayMetrics
        get() = ctx.resources.displayMetrics
    private val screenWidth
        get() = displayMetrics.widthPixels
    private val screenHeight
        get() = displayMetrics.heightPixels

    private val layoutParams
        get() = WindowManager.LayoutParams().apply {
            width = (if (screenWidth > screenHeight) screenWidth * 0.9 else screenWidth).toInt()
            height = (screenHeight * 0.9).toInt()
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.25f
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

    val themedCtx by lazy { ContextThemeWrapper(ctx, R.style.Base_Theme_Extendroid) }
    val layoutInflater by lazy { LayoutInflater.from(themedCtx) }
    val root by lazy {
        layoutInflater.inflate(R.layout.layout_menu, this, true)
    }
    val tabLayout by lazy { root!!.findViewById<TabLayout>(R.id.tabLayout) }
    val recyclerView by lazy { root!!.findViewById<RecyclerView>(R.id.recyclerView) }
    val moreBtn by lazy { root!!.findViewById<ImageView>(R.id.moreBtn) }
    val closeBtn by lazy { root!!.findViewById<ImageView>(R.id.closeBtn) }

    private var closeCallback: () -> Unit = {}
    var collapseSeconds = 30L
    private var autoHideRunnable: Runnable? = null

    private fun setAutoHide() {
        removeAutoHide()
        autoHideRunnable = Runnable { hide() }
        postDelayed(autoHideRunnable!!, collapseSeconds * 1000)
    }

    private fun removeAutoHide() {
        autoHideRunnable?.let { removeCallbacks(it) }
        autoHideRunnable = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListenerForAutoHide() {
        root?.setOnTouchListener { _, _ ->
            setAutoHide()
            false
        }
    }

    val gridColumnCount
        get() = if (ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 3

    val activeWindowsData: ArrayList<WindowData> = arrayListOf()
    val activePackages
        get() = activeWindowsData.map { it.packageName }
    var installedApps = listOf<AppItem>()
    var specialApps = listOf<AppItem>()


    lateinit var staggeredGridAdapter: StaggeredGridAdapter

    init {
        startLoadingInBackground()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.customView?.let {
                    it.findViewById<ConstraintLayout>(R.id.selectedBackground)?.animate()?.alpha(1f)
                    it.findViewById<ImageView>(R.id.tabIcon).animate().translationY(0f)
                    it.findViewById<ImageView>(R.id.tabIconTinted).animate().translationY(0f)
                }
                reloadTabContents(tab!!)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.let {
                    it.findViewById<ConstraintLayout>(R.id.selectedBackground).animate().alpha(0f)
                    it.findViewById<ImageView>(R.id.tabIcon).animate().translationY(ctx.dpToPx(-7f))
                    it.findViewById<ImageView>(R.id.tabIconTinted).animate()
                        .translationY(ctx.dpToPx(-7f))
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}

        })


        listOf(
            R.drawable.logo,
            R.drawable.rounded_star_rate_24,
            R.drawable.outline_add_24
        ).forEachIndexed { index, iconRes ->
            val tab = tabLayout.newTab()
            val custom = layoutInflater.inflate(R.layout.custom_tab_item, null)
            custom.findViewById<ImageView>(if (index == 0) R.id.tabIcon else R.id.tabIconTinted)
                .setImageResource(iconRes)
            tab.customView = custom
            tabLayout.addTab(tab)
            if (index == 0) {
                custom.findViewById<ConstraintLayout>(R.id.selectedBackground).alpha = 1f
                tab.select()
            }
        }


        moreBtn.setOnClickListener {
            when (tabLayout.selectedTabPosition) {
                0 -> showDropdownMenu(it)
            }
        }
        closeBtn.setOnClickListener {
            hide()
        }
    }

    fun startLoadingInBackground() {
        Thread {
            installedApps = PackageManagerHelper.getLaunchableApps(ctx.packageManager)
                .filter { it.packageName != ctx.packageName }
            val topPkgs = getTopApps()
            specialApps = installedApps.filter {
                topPkgs.contains(it.packageName) && !activePackages.contains(it.packageName)
            }
        }.start()
    }

    fun reloadTabContents(tab: TabLayout.Tab) {
        startLoadingInBackground()
        if (tab.position == 0) {
            recyclerView.layoutManager =
                StaggeredGridLayoutManager(gridColumnCount, StaggeredGridLayoutManager.VERTICAL)
            staggeredGridAdapter =
                StaggeredGridAdapter(activeWindowsData, installedApps, onClickClose = { pkg ->
                    activeWindowsData.removeIf { it.packageName == pkg }
                    MediaCore.mInstance?.stopVirtualDisplay(pkg)
                    reloadTabContents(tab)
                }, onClick = { pkg, w, h ->
                    openInPopup(
                        pkg,
                        w,
                        h,
                        Utils.getDominantColorFromDrawable(installedApps.find { it.packageName == pkg }!!.image)
                    )
                    activeWindowsData.removeIf { it.packageName == pkg }
                    hide()
                }, onLongClick = { pkg ->
                    MediaCore.mInstance?.let {
                        it.fullScreen(pkg)
                        activeWindowsData.removeIf { it.packageName == pkg }
                        hide()
                    }
                }, onSurfaceAvailable = { pkg, textureView, width, height ->
                    MediaCore.mInstance?.setupVirtualDisplay(
                        ctx,
                        pkg,
                        Surface(textureView.surfaceTexture),
                        width,
                        height
                    )
                })
            recyclerView.adapter = staggeredGridAdapter

        } else {
            recyclerView.layoutManager =
                androidx.recyclerview.widget.GridLayoutManager(ctx, gridColumnCount)
            if (tab.position == 1) {
                recyclerView.adapter = AppGridAdapter(
                    specialApps
                ) { startPreviewFor(it, newLaunch = true) }
            } else {
                recyclerView.adapter = AppGridAdapter(
                    installedApps.filter { !activePackages.contains(it.packageName) }
                ) { startPreviewFor(it, newLaunch = true) }
            }
        }
        setAutoHide()
    }

    fun showDropdownMenu(anchor: View) {
        PopupMenu(themedCtx, anchor).apply {
            inflate(R.menu.menu_options)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_disable_screen -> {
                        requestDisableScreen()
                    }

                    R.id.action_popup_all -> {
                        val w = staggeredGridAdapter.columnWidth;
                        activeWindowsData.forEach { data->
                            openInPopup(
                                data.packageName,
                                w,
                                (w * data.ratio).toInt(),
                                Utils.getDominantColorFromDrawable(installedApps.find { it.packageName == data.packageName }!!.image)
                            )
                        }
                        activeWindowsData.clear()
                        hide()
                    }

                    R.id.action_fullscreen_all -> {
                        activeWindowsData.forEach { data->
                            MediaCore.mInstance?.fullScreen(data.packageName)
                        }
                        activeWindowsData.clear()
                        hide()
                    }
                    R.id.action_close_all -> {
                        activeWindowsData.forEach { data->
                            MediaCore.mInstance?.stopVirtualDisplay(data.packageName)
                        }
                        activeWindowsData.clear()
                        hide()
                    }
                    R.id.action_start_app -> {
                        requestStartSelf()
                        hide()
                    }
                }
                true
            }
            show()
        }
    }

    fun startPreviewFor(packageName: String, ratio: Float = 1.5f, newLaunch: Boolean = false) {
        if (newLaunch && containsPopup(packageName)) {
            minimisePopup(packageName)
            return
        }
        activeWindowsData.add(
            WindowData(
                installedApps.firstOrNull { it.packageName == packageName }?.appName ?: "",
                packageName,
                ratio
            )
        )
        tabLayout.getTabAt(0)?.select()
        setAutoHide()
    }

    fun setClosedListener(block: () -> Unit) {
        closeCallback = block
    }

    fun onOrientationChanged() {
        if (parent != null) {
            wm.updateViewLayout(this, layoutParams)
            tabLayout.getTabAt(tabLayout.selectedTabPosition)?.let { reloadTabContents(it) }
        }
    }

    fun show() {
        if (parent == null) {
            try {
                scaleY = 0f
                wm.addView(this, layoutParams)
                isShowing = true
                animate().scaleY(1f).setDuration(250).start()
                setAutoHide()
                tabLayout.getTabAt(tabLayout.selectedTabPosition)?.let { reloadTabContents(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            setAutoHide()
        }
        setTouchListenerForAutoHide()
    }

    fun hide() {
        if (parent != null && isShowing) {
            isShowing = false
            animate().scaleY(0f).setDuration(250).start()
            closeCallback.invoke()
            removeAutoHide()
            handler.postDelayed({
                wm.removeView(this)
                handler.postDelayed({
                    scaleY = 1f
                }, 250)
            }, 250)
        }
    }

    companion object {
        var isShowing: Boolean = false
    }


}