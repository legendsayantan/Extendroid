package dev.legendsayantan.extendroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import dev.legendsayantan.extendroid.ShizukuActions.Companion.dispatchMotionEventOnDisplayAdb
import dev.legendsayantan.extendroid.ShizukuActions.Companion.launchComponentOnDisplayAdb
import dev.legendsayantan.extendroid.ShizukuActions.Companion.launchStarterOnDisplayAdb


class ExtendService : Service() {
    val displayCache = hashMapOf<Int, VirtualDisplay>()
    val overlayWorker by lazy { OverlayWorker(this) }
    var idOffset = 0
    var lastId = 0




    override fun onBind(intent: Intent): IBinder? {
        // Implement your binding logic here if needed
        return null
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        MainActivity.refreshStatus()
        startForegroundService()
        // Initialize your media projection here
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection =
            projectionManager.getMediaProjection(result, data!!)
        createVirtualDisplay(mediaProjection!!)
    }

    private fun createVirtualDisplay(mediaProjection: MediaProjection) {
        val metrics = resources.displayMetrics
        val screenDensity = metrics.densityDpi
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        onAttachWindow = { pkg: String, resolution: Pair<Int, Int>, helper: Boolean ->
            val imageReaderNew = ImageReader.newInstance(
                resolution.first, resolution.second,
                PixelFormat.RGBA_8888, 2
            )

            var presentationDisplay: Display? = null
            val newId = ++lastId
            overlayWorker.createWindow(newId, resolution,{ windowSurface->
                val vDisplay = mediaProjection.createVirtualDisplay(
                    pkg,
                    resolution.first,
                    resolution.second,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    windowSurface,
                    null,
                    null
                )

                presentationDisplay = vDisplay.display


                if (presentationDisplay != null) {
                    idOffset = presentationDisplay!!.displayId-newId
                    if (helper) {
                        launchStarterOnDisplayAdb(presentationDisplay!!.displayId, pkg)
                    } else {
                        Utils.launchComponents[pkg]?.let {
                            println("$pkg/$it")
                            launchComponentOnDisplayAdb(presentationDisplay!!.displayId, "$pkg/$it")
                        }
                    }
                } else {
                    println("Presentation display is null")
                }

                imageReaderNew.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    // Do something with the image

                    image?.close()
                }, Handler(Looper.getMainLooper()))

            },{ motionEvent ->
                dispatchMotionEventOnDisplayAdb(newId+idOffset,motionEvent)
            },{ keyCode->
                ShizukuActions.dispatchKeyEventOnDisplayAdb(newId+idOffset, keyCode)
            },{
                onDetachWindow(pkg, newId+idOffset, true)
            })


            presentationDisplay?.displayId ?: -1
        }

        onDetachWindow = { pkg, id, terminate ->
            if(terminate){
                ShizukuActions.dispatchKeyEventOnDisplayAdb(id,KeyEvent.KEYCODE_HOME)
            }
            displayCache[id]?.release()
            displayCache.remove(id)
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notiMan = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notiMan.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                SERVICE_CHANNEL,
                NotificationManager.IMPORTANCE_NONE
            )
        )

        val notification = Notification.Builder(this, SERVICE_CHANNEL)
            .setContentText("Extendroid is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onDestroy() {
        running = false
        // Stop media projection here
        mediaProjection?.stop()
        super.onDestroy()
    }

    companion object {
        var result: Int = 0
        var data: Intent? = null
        var running = false
        private var mediaProjection: MediaProjection? = null
        private const val SERVICE_CHANNEL = "Service"

        //listeners
        var onAttachWindow: (String, Pair<Int, Int>, Boolean) -> Int
                /**DisplayId**/
                = { packageName, resolution, helper -> -1 }
        var onDetachWindow: (String, Int, Boolean) -> Unit =
            { packageName, displayId, terminate -> }
    }
}