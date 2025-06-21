package dev.legendsayantan.extendroid.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import dev.legendsayantan.extendroid.adapters.StaggeredGridAdapter

/**
 * @author legendsayantan
 */
class FreezableRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    // Are we currently “locking” intercept because a drag started on a TextureView?
    private var lockIntercept = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lockIntercept = shouldNotScroll(e)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lockIntercept = false
            }
        }
        return if (lockIntercept) {
            false
        } else {
            super.onInterceptTouchEvent(e)
        }
    }

    private fun shouldNotScroll(e: MotionEvent): Boolean {
        // find the child item under the touch
        val child = findChildViewUnder(e.x, e.y) ?: return false

        // assuming your ViewHolder exposes its TextureView:
        val vh = getChildViewHolder(child) as? StaggeredGridAdapter.ViewHolder ?: return false

        return vh.touchControlsApp
    }
}