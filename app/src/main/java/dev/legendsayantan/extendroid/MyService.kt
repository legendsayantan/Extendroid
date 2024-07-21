package dev.legendsayantan.extendroid

import android.R.attr.height
import android.R.attr.left
import android.R.attr.top
import android.R.attr.width
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaRouter
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.View
import androidx.annotation.RequiresApi
import dev.legendsayantan.extendroid.ShizukuActions.Companion.launchStarterOnDisplay


class MyService : Service() {



    override fun onBind(intent: Intent): IBinder? {
        // Implement your binding logic here if needed
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
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

        val imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            imageReader.surface,
            null,
            null
        )

        var presentationDisplay: Display? = null
        presentationDisplay = virtualDisplay.display


        if (presentationDisplay != null) {
            launchStarterOnDisplay(presentationDisplay)
        } else {
            println("Presentation display is null")
        }

        // Start capturing the screen content
        startScreenCapture(imageReader)
    }
    private fun startScreenCapture(imageReader: ImageReader) {
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.close()
        }, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notiMan = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notiMan.createNotificationChannel(NotificationChannel(CHANNEL_DEFAULT_IMPORTANCE, "Media Projection Service", NotificationManager.IMPORTANCE_DEFAULT))

        val notification = Notification.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
            .setContentTitle("Media Projection Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop media projection here
        mediaProjection?.stop()
    }

    companion object {
        var result : Int = 0
        var data: Intent ? = null
        var displayId : Int = -1
        private var mediaProjection: MediaProjection? = null
        private const val CHANNEL_DEFAULT_IMPORTANCE = "MediaProjectionServiceChannel"
    }
}