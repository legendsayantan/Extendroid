package dev.legendsayantan.extendroid.lib

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.os.Build
import android.util.TypedValue
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration
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
                try{
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkgname", packageName)
                    startActivity(intent)
                }catch (_:ActivityNotFoundException){}
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

        fun getLocalIp(): List<String>{
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            val ipArray = arrayListOf<String>()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.isSiteLocalAddress) {
                        ipArray.add(inetAddress.hostAddress as String)
                    }
                }
            }
            return ipArray
        }

        fun diffBitmaps(bitmap1: Bitmap, bitmap2: Bitmap): Bitmap {
            // Check if the bitmaps have the same dimensions
            if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
                throw IllegalArgumentException("Bitmaps must have the same dimensions")
            }

            // Create a mutable bitmap to store the difference
            val diffBitmap = Bitmap.createBitmap(bitmap1.width, bitmap1.height, Bitmap.Config.ARGB_8888)

            for (y in 0 until bitmap1.height) {
                for (x in 0 until bitmap1.width) {
                    val pixel1 = bitmap1.getPixel(x, y)
                    val pixel2 = bitmap2.getPixel(x, y)

                    // Compare the pixels
                    if (pixel1 != pixel2) {
                        // Highlight the difference in the diffBitmap
                        diffBitmap.setPixel(x, y, pixel2) // You can change the highlight color if you want
                    } else {
                        // If the pixels are the same, set the diffBitmap pixel to transparent
                        diffBitmap.setPixel(x, y, Color.TRANSPARENT)
                    }
                }
            }

            return diffBitmap
        }
    }
}