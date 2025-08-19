package dev.legendsayantan.extendroid.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.MotionEvent
import android.widget.Toast
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils.Companion.toJsonSanitized
import dev.legendsayantan.extendroid.echo.EchoNetworkUtils
import dev.legendsayantan.extendroid.echo.WebRTC
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.ui.FloatingBall
import dev.legendsayantan.extendroid.ui.OverlayMenu
import dev.legendsayantan.extendroid.ui.PopupManager
import org.json.JSONObject
import rikka.shizuku.Shizuku


class ExtendService : Service() {
    val prefs by lazy { Prefs(applicationContext) }
    val ball by lazy { FloatingBall(this) }
    val menu by lazy { OverlayMenu(this) }
    val popupManager by lazy { PopupManager(this) }
    val prefsChangedListener = { ctx: Context ->
        setupPrefsRelated()
    }

    init {
        MediaCore.mInstance = object : MediaCore() {
            override fun mediaProjectionReady() {
                prefs.registerConfigChangeListener(prefsChangedListener)
            }

            override fun virtualDisplayReady(packageName: String, displayID: Int) {
                svc?.launchAppOnDisplay(packageName, displayID)
            }

            override fun appTaskToClear(packageName: String) {
                val r = svc?.exitTasks(packageName);
                println(r)
            }
        }

        //ECHO
        setupEchoCommand = { data, uid, token ->
            val json = JSONObject(data.toJsonSanitized())
            val startEcho = { sdpData: String, uid: String, token: String ->
                //STEP 2. create surface and videoTrack, using res (res is formatted like WIDTHxHEIGHT/SCALE)
                val resolution = json["res"].toString()
                    .split("x", "/")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val width = resolution[0].toIntOrNull() ?: 1280
                val height = resolution[1].toIntOrNull() ?: 720
                val density = (applicationContext.resources.displayMetrics.densityDpi * width * height * (resolution[2].toIntOrNull() ?: 1)) /
                        (applicationContext.resources.displayMetrics.let{ it.widthPixels * it.heightPixels } )
                val objects = WebRTC.createVideoTrackForVirtualDisplay(applicationContext,width,height)
                Thread{
                    WebRTC.start(applicationContext, uid, token, sdpData,objects.second.first,{ state->

                    },{

                    })
                }.start()
                MediaCore.mInstance?.setupEchoDisplay(objects.first,width,height,objects.second.second,density)
            }
            //STEP 1. ensure sdp, ice
            if (json["fetchsdp"] == true || json["fetchsdp"] == "true") {
                //if so, fetch the sdp from the backend
                EchoNetworkUtils.getSignalWithCallback(applicationContext, uid, token) { str, ex ->
                    if (str != null && ex == null) {
                        startEcho(str.toJsonSanitized(), uid, token)
                    } else {
                        //error
                        Handler(applicationContext.mainLooper).post {
                            Toast.makeText(
                                applicationContext,
                                "Error fetching SDP: ${ex?.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                startEcho(data, uid, token)
            }
        }
    }

    val svcArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, RootService::class.java.name))
            .processNameSuffix("rootsvc")
            .debuggable(true)
            .daemon(true)
    }
    var svcConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            println("SVC CONNECT")
            svc = IRootService.Stub.asInterface(binder)
            grantOwnPerms()
            Handler(mainLooper).postDelayed({
                startFGS()
                MediaCore.proceedWithRequest = true
            }, 500)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            println("SVC DISCONNECT")
            svc = null
        }

        override fun onBindingDied(name: ComponentName?) {
            println("SVC DEATH")
            super.onBindingDied(name)
        }
    }
    private val configReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            menu.onOrientationChanged()
        }
    }
    private val menuOpenReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            menu.show()
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerReceiver(configReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                menuOpenReceiver,
                IntentFilter(ACTION_MENU_OPEN),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(menuOpenReceiver, IntentFilter(ACTION_MENU_OPEN))
        }

        bindPrivilegedService()
    }

    fun startFGS() {
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_MENU_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notiMan = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notiMan.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                SERVICE_CHANNEL,
                NotificationManager.IMPORTANCE_NONE
            )
        )
        val notification = Notification.Builder(this, SERVICE_CHANNEL)
            .setContentText("Click to open Extendroid menu")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        setupUI()
    }

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun bindPrivilegedService() {
        if (svc != null) return
        println("SVC INITIATE")
        Shizuku.bindUserService(svcArgs, svcConnection)
    }


    private fun grantOwnPerms() {
        val r = svc?.grantPermissions(
            arrayOf(
                "PROJECT_MEDIA",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "",
                if (!Settings.canDrawOverlays(applicationContext)) Manifest.permission.SYSTEM_ALERT_WINDOW else ""
            ).filter { it.isNotBlank() }
        )
        println(r)
    }

    private fun setupPrefsRelated() {
        menu.collapseSeconds = prefs.collapseSeconds
        if (prefs.floatingBall) {
            ball.show()
        } else {
            ball.hide()
        }
        ball.setOnClickListener {
            menu.show()
            ball.hide()
        }
        menu.setClosedListener {
            if (prefs.floatingBall) {
                ball.show()
            }
        }
    }

    private fun setupUI() {
        setupPrefsRelated()
        menu.getTopApps = {
            svc?.getTopTenApps() ?: listOf()
        }
        menu.openInPopup = { s, w, h, c ->
            popupManager.createNew(s, w, h, c)
        }
        menu.containsPopup = {
            popupManager.hasPopup(it)
        }
        menu.minimisePopup = {
            popupManager.deletePopup(it, PopupManager.PopupDeleteReason.MINIMIZE)
        }
        menu.requestDisableScreen = {
            svc?.setBuiltInDisplayPowerMode(0)
        }
        menu.requestStartSelf = {
            svc?.launchAppOnDisplay(packageName, Display.DEFAULT_DISPLAY)
        }
        menu.dispatchEvent = { pkg, event ->
            print("${event.x} ${event.y} ${MotionEvent.actionToString(event.action)} ${event.rawX} ${event.rawY}")
            println(sendEvent(pkg, event))
        }
        popupManager.onPopupMinimize = { pkg, ratio ->
            menu.startPreviewFor(pkg, ratio)
        }
        popupManager.onKeyEvent = { pkg, keyCode, action ->
            val r = svc?.dispatchKey(
                keyCode,
                action,
                MediaCore.mInstance?.virtualDisplays?.get(pkg)?.display?.displayId ?: -1
            )
            println(r)
        }
        popupManager.onMotionEvent = { pkg, event ->
            print("${event.x} ${event.y} ${event.action} ${event.rawX} ${event.rawY}")
            println(sendEvent(pkg, event))
        }

    }

    fun sendEvent(pkg: String, event: MotionEvent): String {
        return svc?.dispatch(
            event,
            MediaCore.mInstance?.virtualDisplays?.get(pkg)?.display?.displayId ?: -1
        ).toString()
    }

    override fun onDestroy() {
        Shizuku.unbindUserService(svcArgs, svcConnection, true)
        prefs.unregisterConfigChangeListener(prefsChangedListener)
        svc = null
        ball.hide()
        menu.hide()
        super.onDestroy()
    }

    companion object {
        var svc: IRootService? = null
        private const val SERVICE_CHANNEL = "Service"
        private const val ACTION_MENU_OPEN = "dev.legendsayantan.extendroid.action.MENU_OPEN"
        const val ACTION_GET_MEDIA_PROJECTION =
            "dev.legendsayantan.extendroid.action.MEDIA_PROJECTION"

        public var setupEchoCommand: (data: String, uid: String, token: String) -> Unit = { data, uid, token ->
            // Default implementation does nothing
        }
    }
}