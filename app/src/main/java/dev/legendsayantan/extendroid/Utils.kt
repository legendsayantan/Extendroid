package dev.legendsayantan.extendroid

import android.content.Context
import android.content.Intent
import android.util.TypedValue

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
    }
}