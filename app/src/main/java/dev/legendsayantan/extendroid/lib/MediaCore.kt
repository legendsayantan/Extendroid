package dev.legendsayantan.extendroid.lib

import android.app.Activity
import android.content.Context
import android.view.Surface
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.VirtualDisplayNoContentActivity
import dev.legendsayantan.extendroid.echo.RemoteSessionRenderer
import dev.legendsayantan.extendroid.echo.RemoteSessionHandler
import dev.legendsayantan.extendroid.services.ExtendService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/**
 * @author legendsayantan
 */
open class MediaCore {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    var onRunningRemoteAppsUpdate : (String)-> Unit = { id-> }
    var sessionCapturerResizers : HashMap<String,(Int, Int, Int) -> Unit> = hashMapOf()

    var virtualDisplayIds: HashMap<String, Int> = hashMapOf()

    // Changed to store display IDs rather than VirtualDisplay objects
    var echoDisplayIds : HashMap<String, Int> = hashMapOf()

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

        // Communicate with the RootService instead of using MediaProjection
        if (virtualDisplayIds.contains(packageName)) {
            val displayId = virtualDisplayIds[packageName]!!
            ExtendService.svc?.resizeVirtualDisplay(displayId, width, height, density.toInt())
            updateSurface(packageName, surface)
            return
        }

        // Pass the Parcelable Surface across IPC directly into the Shizuku process
        val displayId = ExtendService.svc?.createVirtualDisplay(
            packageName,
            width,
            height,
            density.toInt(),
            surface
        ) ?: -1

        if (displayId != -1) {
            virtualDisplayIds[packageName] = displayId
            virtualDisplayReady(packageName, displayId)
        }
    }

    fun fullScreen(packageName: String) {
        virtualDisplayIds[packageName]?.let {
            VirtualDisplayNoContentActivity.instance?.finish()
            ExtendService.svc?.destroyVirtualDisplay(it)
            virtualDisplayIds.remove(packageName)
        }
    }

    fun stopVirtualDisplay(packageName: String) {
        appTaskToClear(packageName)
        scheduler.schedule({
            fullScreen(packageName)
        }, 250, TimeUnit.MILLISECONDS)
    }

    fun updateSurface(packageName: String, surface: Surface) {
        virtualDisplayIds[packageName]?.let {
            ExtendService.svc?.updateVirtualDisplaySurface(it, surface)
        } ?: throw RuntimeException("Virtual display for $packageName not found")
    }

    fun newSessionRenderer(
        ctx:Context,
        name:String,
        width: Int,
        height: Int,
        scale:Float,
        onDisplayReady: (Int) -> Unit = {}
    ): RemoteSessionRenderer {
        val density = RemoteSessionHandler.computedDensity(ctx, width, height, scale)
        val capturer = RemoteSessionRenderer(
            onSessionCreated = { displayId ->
                echoDisplayIds[name] = displayId
                echoDisplayParams[name] = arrayOf(displayId, width, height, density)
                onDisplayReady(displayId)
            },
            onSessionReleased = {
                // The actual destruction is now safely handled inside RemoteSessionRenderer via ExtendService.svc
                echoDisplayIds.remove(name)
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
        var proceedWithRequest = false
        var mInstance: MediaCore? = null
    }


}