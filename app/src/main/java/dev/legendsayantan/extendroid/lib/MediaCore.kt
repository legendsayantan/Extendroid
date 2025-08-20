package dev.legendsayantan.extendroid.lib

import android.app.Activity
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.MotionEvent
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import dev.legendsayantan.extendroid.Prefs
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
open class MediaCore {

    var projection: MediaProjection? = null
    var virtualDisplays: HashMap<String, VirtualDisplay> = hashMapOf()
    var echoDisplays : HashMap<String,VirtualDisplay> = hashMapOf()
    private fun init(mediaProjection: MediaProjection) {
        projection = mediaProjection
        mediaProjectionReady()
    }

    open fun mediaProjectionReady() {}

    fun setupVirtualDisplay(
        context: Context,
        packageName: String,
        surface: Surface,
        width: Int,
        height: Int
    ) {
        val metrics = context.resources.displayMetrics
        val prefs = Prefs(context)

        val density = if (prefs.densityAuto) {
            (width * height * metrics.densityDpi * prefs.densityScale) / (metrics.heightPixels * metrics.widthPixels)
        } else (metrics.densityDpi * prefs.densityScale)

        if (virtualDisplays.contains(packageName)) {
            virtualDisplays[packageName]!!.resize(
                width,
                height,
                density.toInt()
            )
            updateSurface(packageName, surface)
            return
        }
        projection?.createVirtualDisplay(
            packageName,
            width,
            height,
            density.toInt(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC + DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )?.let {
            virtualDisplays[packageName] = it
            virtualDisplayReady(
                packageName, it.display.displayId
            )
        }
    }

    fun fullScreen(packageName: String) {
        virtualDisplays[packageName]?.let {
            it.release()
            virtualDisplays.remove(packageName)
        }
    }

    fun stopVirtualDisplay(packageName: String) {
        appTaskToClear(packageName)
        Timer().schedule(timerTask { fullScreen(packageName) }, 250)
    }

    fun updateSurface(packageName: String, surface: Surface) {
        virtualDisplays[packageName]?.let {
            it.surface = surface
        } ?: throw RuntimeException("Virtual display for $packageName not found")
    }

    fun setupEchoDisplay(
        name:String,
        width: Int,
        height: Int,
        surface: Surface,
        density: Int
    ) {
        if (echoDisplays.containsKey(name)) {
            echoDisplays[name]?.let {
                it.resize(
                    width,
                    height,
                    density
                )
                it.surface = surface
            }

            return
        }else{
            projection?.createVirtualDisplay(
                name,
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface,
                null,
                null
            )?.let {
                echoDisplays[name] = it
            }
        }

    }



    open fun virtualDisplayReady(packageName: String, displayID: Int) {}
    open fun appTaskToClear(packageName: String) {}

    companion object {
        const val REQUEST_CODE = 23
        var proceedWithRequest = false
        var intent: Intent? = null
        var mInstance: MediaCore? = null
        var projectionManager: MediaProjectionManager? = null
        fun onMediaProjectionResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
                intent = data
                projectionManager?.getMediaProjection(
                    resultCode, data
                )?.let {
                    mInstance?.init(
                        it
                    )
                }
            } else {
                throw RuntimeException("Permission denied")
            }
        }

        fun AppCompatActivity.requestMediaProjection() {
            Thread {
                while (!proceedWithRequest) {
                    Thread.sleep(500)
                }
                projectionManager =
                    getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

                val captureIntent = projectionManager!!.createScreenCaptureIntent()
                startActivityForResult(captureIntent, REQUEST_CODE)
            }.start()
        }
    }


}