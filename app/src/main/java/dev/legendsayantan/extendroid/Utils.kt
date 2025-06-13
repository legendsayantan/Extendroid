package dev.legendsayantan.extendroid


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import java.util.Locale

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

        fun Context.miuiRequirements(){
            if ("xiaomi" == Build.MANUFACTURER.lowercase(Locale.ROOT)) {
                Toast.makeText(this,"Please allow the required miui permissions!",Toast.LENGTH_SHORT).show()
                try{
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkgname", packageName)
                    startActivity(intent)
                }catch (_:ActivityNotFoundException){}
            }
        }
    }
}