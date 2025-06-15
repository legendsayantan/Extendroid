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
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.ui.FloatingBall
import dev.legendsayantan.extendroid.ui.OverlayMenu
import dev.legendsayantan.extendroid.ui.PopupManager
import rikka.shizuku.Shizuku


class ExtendService : Service() {
    val prefs by lazy { Prefs(applicationContext) }
    val ball by lazy { FloatingBall(this) }
    val menu by lazy { OverlayMenu(this) }
    val popupManager by lazy { PopupManager(this) }
    val prefsChangedListener = { ctx: Context->
        setupUI()
    }

    init {
        MediaCore.mInstance = object : MediaCore() {
            override fun mediaProjectionReady() {
                //prefs.registerChangeListener(prefsChangedListener)
            }

            override fun virtualDisplayReady(packageName: String, displayID: Int) {
                svc?.launchAppOnDisplay(packageName,displayID)
            }

            override fun appTaskToClear(packageName: String) {
                val r = svc?.exitTasks(packageName);
                println(r)
            }
        }
    }

    val svcArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, RootService::class.java.name))
            .processNameSuffix("$packageName.services:rootsvc")
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

    fun startFGS(){
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
                if (!Settings.canDrawOverlays(applicationContext)) Manifest.permission.SYSTEM_ALERT_WINDOW else ""
            ).filter { it.isNotBlank() }
        )
        println(r)
    }

    private fun setupUI() {
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
        menu.getTopApps = {
            svc?.getTopTenApps() ?: listOf()
        }
        menu.openInPopup = { s, w, h, c ->
            popupManager.createNew(s, w, h, c )
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
            svc?.launchAppOnDisplay(packageName,Display.DEFAULT_DISPLAY)
        }
        popupManager.onPopupMinimize = { pkg,ratio->
            menu.startPreviewFor(pkg,ratio)
        }
        popupManager.onKeyEvent = { pkg,keyCode,action->
            val r = svc?.dispatchKey(keyCode, action, MediaCore.mInstance?.virtualDisplays?.get(pkg)?.display?.displayId?:-1)
            println(r)
        }
        popupManager.onMotionEvent = { pkg,event->
            val r = svc?.dispatch(event,MediaCore.mInstance?.virtualDisplays?.get(pkg)?.display?.displayId?:-1)
            println(r)
        }

    }

    override fun onDestroy() {
        Shizuku.unbindUserService(svcArgs, svcConnection, true)
        prefs.unregisterChangeListener(prefsChangedListener)
        svc = null
        ball.hide()
        menu.hide()
        super.onDestroy()
    }

    companion object {
        var svc: IRootService? = null
        private const val SERVICE_CHANNEL = "Service"
        private const val ACTION_MENU_OPEN = "dev.legendsayantan.extendroid.action.MENU_OPEN"
        const val ACTION_GET_MEDIA_PROJECTION = "dev.legendsayantan.extendroid.action.MEDIA_PROJECTION"
    }
}