package dev.legendsayantan.extendroid.lib

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import android.util.TypedValue
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.util.Locale


/**
 * @author legendsayantan
 */
class Utils {
    companion object{
        val launchComponents = hashMapOf<String,String>()
        fun Context.getLaunchableApps(): HashMap<String, String> {
            val launchableApps: HashMap<String, String> = hashMapOf()
            val packageManager = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val appsList = packageManager.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in appsList) {
                val app = resolveInfo.activityInfo.applicationInfo
                if (app != null && app.packageName != packageName) {
                    launchableApps[app.packageName] = app.loadLabel(packageManager).toString()
                    launchComponents[app.packageName] = resolveInfo.activityInfo.name
                }
            }
            return launchableApps
        }
        fun Context.dpToPx(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
        }

        fun Context.getAppNameFromPackage(pkg:String):String{
            val packageManager = packageManager
            val app = packageManager.getApplicationInfo(pkg, 0)
            return app.loadLabel(packageManager).toString()
        }

        fun Context.getAppIconFromPackage(pkg:String) = packageManager.getApplicationIcon(pkg)

        fun Context.getBackgroundColor(): Int {
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            return typedValue.data
        }
        fun Context.getForegroundColor():Int {
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.textColor, typedValue, true)
            return typedValue.data
        }

        fun Context.miuiRequirements(){
            if ("xiaomi" == Build.MANUFACTURER.lowercase(Locale.ROOT)) {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                intent.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity")
                intent.putExtra("extra_pkgname", packageName)
                startActivity(intent)
            }
        }
        private var bitmapBuffer: Bitmap? = null
        fun toBitmap(image: Image): Bitmap {
            if (bitmapBuffer == null) {
                bitmapBuffer = Bitmap.createBitmap(
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
            }
            bitmapBuffer!!.copyPixelsFromBuffer(image.planes[0].buffer)
            return bitmapBuffer!!
        }
    }
}