package dev.legendsayantan.extendroid


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.TypedValue
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import rikka.shizuku.Shizuku
import java.util.Locale

/**
 * @author legendsayantan
 */
class Utils {
    companion object {

        fun isShizukuSetup(): Boolean {
            return Shizuku.pingBinder()
        }

        fun isShizukuAllowed(): Boolean {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }

        fun whenSafeForUI(ctx: Context, then:()-> Unit){
            if(isShizukuSetup() && isShizukuAllowed()){
                if(!Settings.canDrawOverlays(ctx)){
                    Toast.makeText(ctx,"Please wait, starting soon!", Toast.LENGTH_LONG).show()
                    Handler(ctx.mainLooper).postDelayed({
                        whenSafeForUI(ctx,then)
                    },2000)
                }
                then()
            }else{
                Toast.makeText(ctx,"Failure: Insufficient Setup!", Toast.LENGTH_LONG).show()
            }
        }

        fun getColorOf(drawable: Drawable): Int {
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val palette = Palette.from(bitmap).generate()
            return palette.getDominantColor(0xFFFFFF) // default to white if not found
        }

        fun mixColors(c1: Int = Color.TRANSPARENT, c2: Int, ratio: Float): Int {
            val inverseRatio = 1f - ratio
            val a = (Color.alpha(c1) * inverseRatio + Color.alpha(c2) * ratio).toInt()
            val r = (Color.red(c1) * inverseRatio + Color.red(c2) * ratio).toInt()
            val g = (Color.green(c1) * inverseRatio + Color.green(c2) * ratio).toInt()
            val b = (Color.blue(c1) * inverseRatio + Color.blue(c2) * ratio).toInt()
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