package dev.legendsayantan.extendroid

import android.content.Context
import android.content.Intent

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
    }
}