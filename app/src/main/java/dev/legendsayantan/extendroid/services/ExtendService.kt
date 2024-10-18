package dev.legendsayantan.extendroid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.legendsayantan.extendroid.MainActivity
import dev.legendsayantan.extendroid.lib.OverlayWorker
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.lib.ShizukuActions
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.dispatchMotionEventOnDisplayAdb
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.launchComponentOnDisplayAdb
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.launchStarterOnDisplayAdb
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.setMainDisplayPowerMode
import dev.legendsayantan.extendroid.lib.Utils
import dev.legendsayantan.extendroid.data.ActiveSession
import dev.legendsayantan.extendroid.data.TouchMotionEvent
import dev.legendsayantan.extendroid.data.TouchMotionEvent.Companion.asMotionEvent
import dev.legendsayantan.extendroid.lib.StreamHandler
import dev.legendsayantan.extendroid.lib.UdpServer
import java.io.ByteArrayOutputStream


class ExtendService : Service() {
    val displayCache = hashMapOf<Int, VirtualDisplay>()
    val overlayWorker by lazy { OverlayWorker(this) }
    var idToDisplayIdMap = hashMapOf<Int, Int>()
    var activeSessions = arrayListOf<ActiveSession>()
    var lastId = 0
    lateinit var motionDispatcher: Thread
    lateinit var keyDispatcher: Thread
    var motionEvents: ArrayList<Pair<Int, MotionEvent>> = arrayListOf()
    var motionData: ArrayList<Pair<Int, ByteArray>> = arrayListOf()
    var keyEvents: ArrayList<Pair<Int, Int>> = arrayListOf()
    val gson by lazy { GsonBuilder().setLenient().create() }
    val udpServer by lazy {
        UdpServer { id, type, data ->
            when (type) {
                UdpServer.Type.MOTIONEVENT -> {
                    motionData.add(Pair(idToDisplayIdMap[id]!!, data))
                }

                else -> {}
            }
        }
    }

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
        if (data == null) {
            stopSelf()
            return
        }
        mediaProjection =
            projectionManager.getMediaProjection(result, data!!)

        queryWindows = {
            activeSessions
        }
        if (MainActivity.shouldShowMenu()) {
            overlayWorker.showMenu({
                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("add", true)
                )
            }, {
                setMainDisplayPowerMode(0)
            })
        }
        motionDispatcher = Thread {
            while (true) {
                if (motionEvents.isNotEmpty()) {
                    val event = motionEvents.removeAt(0)
                    dispatchMotionEventOnDisplayAdb(event.first, event.second)
                }
                if (motionData.isNotEmpty()) {
                    val event = motionData.removeAt(0)
                    var json = String(event.second)
                    json = json.filter { it != '\u0000' && it!='?' }
                    println(json)
                    json = json.substring(0,json.indexOf('}')+1)
                    println(json)
                    val motionEvent = gson.fromJson(
                        json,
                        TypeToken.get(TouchMotionEvent::class.java)
                    ).asMotionEvent()
                    dispatchMotionEventOnDisplayAdb(event.first, motionEvent)
                }
            }
        }
        keyDispatcher = Thread {
            while (true) {
                if (keyEvents.isNotEmpty()) {
                    val event = keyEvents.removeAt(0)
                    ShizukuActions.dispatchKeyEventOnDisplayAdb(event.first, event.second)
                }
            }
        }
        motionDispatcher.start()
        keyDispatcher.start()

        createVirtualDisplay(mediaProjection!!)
    }

    private fun createVirtualDisplay(mediaProjection: MediaProjection) {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        onAttachWindow = { pkg, resolution, density, helper, windowMode, startMinimized, callback ->
            val newId = ++lastId
            val startAndSaveSession: (Display, Int) -> Unit = { d, port ->
                idToDisplayIdMap[newId] = d.displayId
                activeSessions.add(
                    ActiveSession(
                        d.displayId,
                        pkg,
                        "${resolution.first}x${resolution.second}",
                        windowMode,
                        port
                    )
                )
                if (helper) {
                    launchStarterOnDisplayAdb(d.displayId, pkg)
                } else {
                    Utils.launchComponents[pkg]?.let {
                        println("$pkg/$it")
                        launchComponentOnDisplayAdb(d.displayId, "$pkg/$it")
                    }
                }
                callback(d.displayId)
            }
            if (windowMode != WindowMode.WIRELESS) {
                overlayWorker.createWindow(newId, resolution, pkg, windowMode, { windowSurface ->
                    if (resolution.first > screenWidth || resolution.second > screenHeight) {
                        do {
                            val newParam = overlayWorker.scaleWindow(newId, false)
                        } while (newParam.height > screenHeight || newParam.width > screenWidth)
                    }
                    val vDisplay = mediaProjection.createVirtualDisplay(
                        pkg,
                        resolution.first,
                        resolution.second,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        windowSurface,
                        null,
                        null
                    )

                    val presentationDisplay = vDisplay.display


                    if (presentationDisplay != null) {
                        startAndSaveSession(
                            presentationDisplay,
                            if (windowMode == WindowMode.BOTH) {
                                overlayWorker.startStreaming(newId, udpServer)
                            } else -1
                        )
                    } else {
                        println("Presentation display is null")
                    }

                }, { motionEvent ->
                    motionEvents.add(Pair(idToDisplayIdMap[newId] ?: -1, motionEvent))
                }, { keyCode ->
                    keyEvents.add(Pair(idToDisplayIdMap[newId] ?: -1, keyCode))
                }, {
                    onDetachWindow(idToDisplayIdMap[newId] ?: -1, true)
                    MainActivity.refreshStatus()
                })
                Handler(applicationContext.mainLooper).post {
                    if(startMinimized){
                        overlayWorker.minimize(newId)
                    }
                }
            } else {
                val imageReaderNew = ImageReader.newInstance(
                    resolution.first, resolution.second,
                    PixelFormat.RGBA_8888, 2
                )
                val vDisplay = mediaProjection.createVirtualDisplay(
                    pkg,
                    resolution.first,
                    resolution.second,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    imageReaderNew.surface,
                    null,
                    null
                )
                val presentationDisplay = vDisplay.display
                if (presentationDisplay != null) {
                    startAndSaveSession(
                        presentationDisplay,
                        StreamHandler.start(
                            newId,
                            imageReader = imageReaderNew,
                            udpServer = udpServer
                        )
                    )
                }
            }
        }

        onDetachWindow = { id, terminate ->
            if (terminate) {
                ShizukuActions.dispatchKeyEventOnDisplayAdb(id, KeyEvent.KEYCODE_HOME)
                displayCache[id]?.release()
                displayCache.remove(id)
            }
            idToDisplayIdMap.filter { it.value == id }.keys.firstOrNull()?.let {
                overlayWorker.deleteWindow(it)
                StreamHandler.stop(it, udpServer)
            }
            activeSessions.removeIf { it.id == id }
        }

        StreamHandler.onFrameAvailable = { id, quality, image ->
            if (image != null) {
                val stream = ByteArrayOutputStream(image.byteCount)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    image.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
                } else image.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                udpServer.send(id, stream.toByteArray())
            }
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
        try {
            motionDispatcher.interrupt()
        } catch (_: Exception) {
        }
        try {
            keyDispatcher.interrupt()
        } catch (_: Exception) {
        }
        // Stop media projection here
        mediaProjection?.stop()
        overlayWorker.hideMenu()
        udpServer.closeAll()
        MainActivity.refreshStatus()
        super.onDestroy()
    }

    companion object {
        enum class WindowMode {
            POPUP, WIRELESS, BOTH
        }

        var result: Int = 0
        var data: Intent? = null
        var running = false
        private var mediaProjection: MediaProjection? = null
        private const val SERVICE_CHANNEL = "Service"

        //listeners
        var onAttachWindow: (String, Pair<Int, Int>, Int, Boolean, WindowMode, Boolean, (Int) -> Unit ) -> Unit =
            { packageName, resolution, density, helper, mode, startMinimized, callback -> }
        var onDetachWindow: (Int, Boolean) -> Unit =
            { displayId, terminate -> }
        var queryWindows: () -> List<ActiveSession> = { listOf() }
    }
}