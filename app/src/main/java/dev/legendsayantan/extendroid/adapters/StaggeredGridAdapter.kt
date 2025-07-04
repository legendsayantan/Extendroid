package dev.legendsayantan.extendroid.adapters

// StaggeredGridAdapter.kt

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.model.AppItem
import dev.legendsayantan.extendroid.model.WindowData


/**
 * @author legendsayantan
 */
class StaggeredGridAdapter(
    private val ctx: Context,
    private val windowData: List<WindowData>,
    private val allApps: List<AppItem>,
    private val onClickClose: (String) -> Unit,
    private val onClick: (String, Int, Int) -> Unit,
    private val onLongClick: (String) -> Unit,
    private val onTouchEvent: (String, MotionEvent) -> Unit,
    private val onSurfaceAvailable: (String, TextureView, Int, Int) -> Unit
) :
    RecyclerView.Adapter<StaggeredGridAdapter.ViewHolder>() {

    var columnWidth = 0

    override fun getItemId(position: Int): Long = position.toLong()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_app_preview, parent, false)

        // Determine span count based on orientation
        val spanCount =
            if (parent.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 3

        // Calculate column width: total width minus paddings, divided by span count
        val totalSpace = parent.measuredWidth - parent.paddingStart - parent.paddingEnd
        columnWidth = totalSpace / spanCount

        return ViewHolder(
            view, ctx,
            onClickClose,
            onClick,
            onLongClick,
            onTouchEvent,
            onSurfaceAvailable
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = windowData[position]
        val color = allApps.firstOrNull { it.packageName == item.packageName }?.image?.let {
            Utils.getColorOf(it)
        }
        holder.bind(
            item,
            columnWidth,
            color ?: Color.TRANSPARENT,
        )
    }

    override fun getItemCount(): Int = windowData.size

    class ViewHolder(
        itemView: View,
        val ctx: Context,
        val onClickClose: (String) -> Unit,
        val onClick: (String, Int, Int) -> Unit,
        val onLongClick: (String) -> Unit,
        val onTouchEvent: (String, MotionEvent) -> Unit,
        val onItemAttached: (String, TextureView, Int, Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        var packageName = ""
        var height = 0
        var touchControlsApp = false
        private val parentCard = itemView.findViewById<MaterialCardView>(R.id.parentCard)
        val textureView: TextureView = itemView.findViewById<TextureView>(R.id.surfaceContainer)
        private val textView: TextView = itemView.findViewById(R.id.text)
        private val gradientArea: LinearLayout =
            itemView.findViewById<LinearLayout>(R.id.gradientArea)
        private val actionBtn: ImageView = itemView.findViewById(R.id.actionBtn)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            windowData: WindowData,
            columnWidth: Int,
            color: Int
        ) {
            packageName = windowData.packageName
            height = (columnWidth * windowData.ratio).toInt()
            parentCard.strokeColor = color
            textView.text = windowData.name
            val layoutParams = itemView.layoutParams
            layoutParams.height = height
            itemView.layoutParams = layoutParams
            gradientArea.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Utils.mixColors(c2 = color, ratio = 0.85f), color)
            ).apply {
                setGradientType(android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT)
            }

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    onItemAttached(packageName, textureView, columnWidth, height)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

            }

            actionBtn.setOnClickListener {
                gradientArea.visibility = View.GONE
                touchControlsApp = true
                parentCard.requestDisallowInterceptTouchEvent(true)
                parentCard.parent.requestDisallowInterceptTouchEvent(true)
                textureView.setOnTouchListener { v, event ->
                    onTouchEvent(windowData.packageName, event)
                    true
                }
            }
            actionBtn.setOnLongClickListener {
                onClickClose(windowData.packageName)
                true
            }
            parentCard.setOnClickListener {
                if (!touchControlsApp)
                    onClick(windowData.packageName, columnWidth, height)
            }
            parentCard.setOnLongClickListener {
                if (!touchControlsApp)
                    onLongClick(windowData.packageName);
                true
            }
        }
    }
}