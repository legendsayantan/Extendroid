package dev.legendsayantan.extendroid.lib

import android.app.Activity
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.echo.RemoteSessionRenderer
import dev.legendsayantan.extendroid.echo.RemoteSessionHandler
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
open class MediaCore {

    var onRunningRemoteAppsUpdate : (String)-> Unit = { id-> }
    var sessionCapturerResizers : HashMap<String,(Int, Int, Int) -> Unit> = hashMapOf()

    var projection: MediaProjection? = null
    var virtualDisplays: HashMap<String, VirtualDisplay> = hashMapOf()
    var echoDisplays : HashMap<String,VirtualDisplay> = hashMapOf()

    /**
     * This map is used to store the display parameters for each echo display.
     * array elements are:
     * 0: Display ID
     * 1: Width
     * 2: Height
     * 3: density
     */
    var echoDisplayParams : HashMap<String, Array<Int>> = hashMapOf()
    var appRemoteAccessHistory = object : HashMap<String,List<String>>() {
        override fun put(key: String, value: List<String>): List<String>? {
            val x = super.put(key, value)
            onRunningRemoteAppsUpdate(key)
            return x;
        }

        override fun remove(key: String): List<String>? {
            val x = super.remove(key)
            onRunningRemoteAppsUpdate(key)
            return x;
        }
    }
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

    fun newSessionRenderer(
        ctx:Context,
        name:String,
        width: Int,
        height: Int,
        scale:Float,
        onMediaProjectionStopped: () -> Unit
    ): RemoteSessionRenderer {
        val mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                onMediaProjectionStopped()
            }
        }

        val density = RemoteSessionHandler.computedDensity(ctx, width, height, scale)
        val capturer = RemoteSessionRenderer(
            mediaProjection = projection!!,
            mediaProjectionCallback = mediaProjectionCallback,
            { virtualDisplay ->
                echoDisplays[name] = virtualDisplay
                echoDisplayParams[name] = arrayOf(virtualDisplay.display.displayId,width,height,density)
            },{
                echoDisplays[name]?.release()
                echoDisplays.remove(name)
                echoDisplayParams.remove(name)
            },
            displayName = name,
            displayDpi = density
        )
        sessionCapturerResizers[name] = { w, h, d ->
            capturer.updateDimensions(w, h, d)
        }
        return capturer
    }



    open fun virtualDisplayReady(packageName: String, displayID: Int) {}
    open fun appTaskToClear(packageName: String) {}

    companion object {
        const val REQUEST_CODE = 23
        var proceedWithRequest = false
        var mInstance: MediaCore? = null
        var projectionManager: MediaProjectionManager? = null
        fun onMediaProjectionResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
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