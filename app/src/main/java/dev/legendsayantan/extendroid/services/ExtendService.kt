package dev.legendsayantan.extendroid.services

import android.Manifest
import android.app.ActivityOptions
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
import android.view.WindowManager
import dev.legendsayantan.extendroid.MainActivity
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.VirtualDisplayNoContentActivity
import dev.legendsayantan.extendroid.R
import dev.legendsayantan.extendroid.Utils
import dev.legendsayantan.extendroid.WorkspaceActivity
import dev.legendsayantan.extendroid.echo.RemoteSessionHandler
import dev.legendsayantan.extendroid.echo.RemoteUnlocker
import dev.legendsayantan.extendroid.echo.WebRTC
import dev.legendsayantan.extendroid.lib.ActivityHelper
import dev.legendsayantan.extendroid.lib.Logging
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.ui.FloatingBall
import dev.legendsayantan.extendroid.ui.OverlayMenu
import dev.legendsayantan.extendroid.ui.PopupManager
import org.webrtc.PeerConnection.IceConnectionState.*;
import org.webrtc.VideoCapturer
import rikka.shizuku.Shizuku


class ExtendService : Service() {
    val prefs by lazy { Prefs(applicationContext) }
    val overlayContext: Context by lazy {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val primaryDisplay = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val displayContext = createDisplayContext(primaryDisplay!!)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            displayContext.createWindowContext(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
    }
    val ball by lazy { FloatingBall(overlayContext) }
    val menu by lazy { OverlayMenu(android.view.ContextThemeWrapper(overlayContext, R.style.Theme_Extendroid)) }
    val popupManager by lazy { PopupManager(overlayContext) }
    val prefsChangedListener = { ctx: Context ->
        setupPrefsRelated()
    }

    val logging by lazy { Logging(applicationContext) }

    init {
        MediaCore.mInstance = object : MediaCore() {
            override fun virtualDisplayReady(packageName: String, displayID: Int) {
                svc?.launchAppOnDisplay(packageName, displayID)
            }

            init {
                this.onNeedNewTab = { pkg ->
                    Utils.whenSafeForUI(this@ExtendService) {
                        menu.startPreviewFor(pkg, 1.5f, true)
                    }
                }
            }

            override fun appTaskToClear(packageName: String) {
                val r = svc?.exitTasks(packageName);
                println(r)
            }
        }

        //ECHO
        setupEchoCommand = { data, uid, token ->
            val connectionId = System.currentTimeMillis()
            var width = 0
            var height = 0
            var capturer: VideoCapturer? = null
            if (data["res"] != null) {
                val resolution = data["res"].toString()
                    .split("x", "/")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                width = resolution[0].toIntOrNull() ?: 1280
                height = resolution[1].toIntOrNull() ?: 720
                val scale = resolution[2].toFloatOrNull() ?: 1f
                capturer =
                    MediaCore.mInstance!!.newSessionRenderer(
                        applicationContext,
                        connectionId.toString(),
                        width,
                        height,
                        scale,
                        onDisplayReady = { displayId ->
                            Handler(mainLooper).postDelayed({
                                try {
                                    val componentName = ComponentName(this@ExtendService, VirtualDisplayNoContentActivity::class.java)
                                    svc?.launchComponentOnDisplay(componentName, displayId, Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                                    logging.i("Launched NoContentActivity via svc on display $displayId", "ExtendService")
                                } catch (e: Exception) {
                                    logging.e(e, "ExtendService")
                                }
                            }, 300)
                        }
                    )
            }

            WebRTC.checkAndStart(
                applicationContext,
                connectionId,
                uid,
                token,
                data,
                capturer,
                width,
                height,
                MainActivity.refreshRate.coerceAtMost(60),
                { state ->
                    if (listOf(DISCONNECTED, CLOSED, FAILED).contains(state)) {
                        RemoteSessionHandler.shutDownRemoteSession(
                            connectionId.toString(),
                            MediaCore.mInstance!!,
                            svc!!
                        )
                    }
                    showServiceNotification(WebRTC.getPeerConnectionCount())
                }, {
                    RemoteSessionHandler.handleDataChannel(
                        applicationContext,
                        connectionId.toString(),
                        MediaCore.mInstance!!,
                        it,
                        width == 0 && height == 0
                    )
                },
                {
                    RemoteSessionHandler.processDataMessage(
                        applicationContext,
                        connectionId.toString(),
                        it,
                        MediaCore.mInstance!!,
                        svc!!
                    )
                })
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
            logging.i("Connnected to RootService.","ExtendService")
            svc = IRootService.Stub.asInterface(binder)
            grantOwnPerms()
            Handler(mainLooper).postDelayed({
                startAsForegroundService()
                setupUI()
                MediaCore.proceedWithRequest = true
            }, 500)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logging.i("Disconnnected from RootService.","ExtendService")
            svc = null
        }

        override fun onBindingDied(name: ComponentName?) {
            logging.i("RootService Died.","ExtendService")
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

        prefs.registerConfigChangeListener(prefsChangedListener)

        bindPrivilegedService()
    }


    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun bindPrivilegedService() {
        if (svc != null) return
        logging.i("Attempting to start RootService.","ExtendService")
        Shizuku.bindUserService(svcArgs, svcConnection)
    }


    private fun grantOwnPerms() {
        val permissions = mutableListOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "",
            if (!Settings.canDrawOverlays(applicationContext)) Manifest.permission.SYSTEM_ALERT_WINDOW else "",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE
        )
        val r = svc?.grantPermissions(permissions.filter { it.isNotBlank() })
        logging.d(r.toString(),"ExtendService")
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
        menu.requestNewWorkspace = {
            Utils.showWorkspaceNameDialog(this) { name ->
                val intent = Intent(this, WorkspaceActivity::class.java).apply {
                    putExtra(WorkspaceActivity.EXTRA_WORKSPACE_NAME, name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
        menu.dispatchEvent = { pkg, event ->
            sendEvent(pkg, event)
        }
        popupManager.onPopupMinimize = { pkg, ratio ->
            menu.startPreviewFor(pkg, ratio)
        }
        popupManager.onKeyEvent = { pkg, keyCode, action ->
            svc?.dispatchKey(
                keyCode,
                action,
                MediaCore.mInstance?.virtualDisplayIds?.get(pkg) ?: -1, 0
            )
        }
        popupManager.onMotionEvent = { pkg, event ->
            sendEvent(pkg, event)
        }

        val taskRunner = dev.legendsayantan.extendroid.lib.TaskRunner(applicationContext)
        val mainHandler = Handler(mainLooper)
        taskRunner.onCloseTab = { pkg ->
            mainHandler.post {
                Utils.whenSafeForUI(this) {
                    menu.closePreviewFor(pkg)
                }
            }
        }
        menu.runTask = { task ->
            taskRunner.run(task, svc!!,
                onNeedNewTab = { pkg ->
                    mainHandler.post {
                        Utils.whenSafeForUI(this) {
                            menu.startPreviewFor(pkg, task.displayHeight.toFloat() / task.displayWidth, true)
                        }
                    }
                },
                onStarted = { mainHandler.post { Utils.whenSafeForUI(this) { android.widget.Toast.makeText(this, getString(R.string.task_started_toast, task.taskKey), android.widget.Toast.LENGTH_SHORT).show() } } },
                onDone    = { mainHandler.post { Utils.whenSafeForUI(this) { android.widget.Toast.makeText(this, getString(R.string.task_done_toast, task.taskKey), android.widget.Toast.LENGTH_SHORT).show() } } },
                onError   = { msg -> mainHandler.post { Utils.whenSafeForUI(this) { android.widget.Toast.makeText(this, getString(R.string.task_error_toast, task.taskKey, msg), android.widget.Toast.LENGTH_LONG).show() } } }
            )
        }
        menu.deleteTask = { task ->
            dev.legendsayantan.extendroid.lib.TaskManager.deleteTask(applicationContext, task.pkgName, task.taskKey)
        }
        menu.updateTask = { task ->
            dev.legendsayantan.extendroid.lib.TaskManager.updateTask(applicationContext, task)
        }
        menu.startKeyEventRecording = { cb ->
            svc?.registerKeyEventListener(object : dev.legendsayantan.extendroid.IKeyEventCallback.Stub() {
                override fun onKeyEvent(json: String) {
                    val obj = org.json.JSONObject(json)
                    cb(obj.getInt("action"), obj.getInt("keyCode"), obj.getInt("metaState"),
                       System.currentTimeMillis() - menu.recordingStartTime)
                }
            })
        }
        menu.stopKeyEventRecording = { svc?.unregisterKeyEventListener() }

    }

    fun sendEvent(pkg: String, event: MotionEvent): String {
        return svc?.dispatch(
            event,
            MediaCore.mInstance?.virtualDisplayIds?.get(pkg) ?: -1
        ).toString()
    }

    override fun onDestroy() {
        svc?.unregisterMotionEventListener()
        
        MediaCore.mInstance?.let { core ->
            svc?.let { rootSvc ->
                core.echoDisplayIds.keys.toList().forEach { connectionId ->
                    RemoteSessionHandler.shutDownRemoteSession(connectionId, core, rootSvc)
                }
            }
        }
        WebRTC.closeAll()

        MediaCore.mInstance = null
        Shizuku.unbindUserService(svcArgs, svcConnection, true)
        unregisterReceiver(configReceiver)
        unregisterReceiver(menuOpenReceiver)
        prefs.unregisterConfigChangeListener(prefsChangedListener)
        svc = null
        ball.hide()
        menu.hide()
        setupEchoCommand = { _, _, _ -> }
        super.onDestroy()
    }

    companion object {
        var svc: IRootService? = null

        lateinit var svcIntent: Intent
        private const val SERVICE_CHANNEL = "Service"
        private const val SERVICE_CHANNEL_LOW_PRIORITY = "Service_Silent"
        private const val ACTION_MENU_OPEN = "dev.legendsayantan.extendroid.action.MENU_OPEN"
        const val ACTION_GET_MEDIA_PROJECTION =
            "dev.legendsayantan.extendroid.action.MEDIA_PROJECTION"

        var setupEchoCommand: (data: Map<String, String>, uid: String, token: String) -> Unit =
            { data, uid, token ->
                // Default implementation does nothing
            }

        fun Context.createNoti(echoRemoteCount: Int = 0): Notification {
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
            notiMan.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_LOW_PRIORITY,
                    SERVICE_CHANNEL_LOW_PRIORITY,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null,null)
                }
            )
            val notification = Notification.Builder(this, if (echoRemoteCount > 0) SERVICE_CHANNEL else SERVICE_CHANNEL_LOW_PRIORITY)
                .setContentTitle("Click to open Extendroid menu")
                .let { if (echoRemoteCount > 0) it.setContentText("Echo has $echoRemoteCount active connections.") else it }
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent)
                .build()
            return notification
        }


        fun Service.startAsForegroundService() {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(1, createNoti(), type)
        }

        fun Context.showServiceNotification(echoRemoteCount: Int) {
            val n = createNoti(echoRemoteCount)
            val notiMan = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notiMan.notify(1,n)
        }
    }
}