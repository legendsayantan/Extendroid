package dev.legendsayantan.extendroid


import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette

/**
 * @author legendsayantan
 */
class Utils {
    companion object {
        fun getDominantColorFromDrawable(drawable: Drawable): Int {
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val palette = Palette.from(bitmap).generate()
            return palette.getDominantColor(0xFFFFFF) // default to white if not found
        }

        fun mixColors(color1: Int, color2: Int, ratio: Float): Int {
            val inverseRatio = 1f - ratio
            val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
            val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
            val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
            val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
            return Color.argb(a, r, g, b)
        }

        fun isDarkModeEnabled(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        fun Context.dpToPx(dp: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
        }
    }
}