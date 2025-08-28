package dev.legendsayantan.extendroid.services

import android.content.Context
import android.view.MotionEvent
import dev.legendsayantan.extendroid.IEventCallback
import dev.legendsayantan.extendroid.echo.MotionEventParser
import dev.legendsayantan.extendroid.lib.ActivityHelper
import dev.legendsayantan.extendroid.lib.DevInputReader
import dev.legendsayantan.extendroid.lib.DisplayHelper
import dev.legendsayantan.extendroid.lib.InputEventListener
import dev.legendsayantan.extendroid.lib.InputEvent
import dev.legendsayantan.extendroid.lib.PackageManagerHelper
import java.io.BufferedReader
import java.io.InputStreamReader


class RootService() : IRootService.Stub() {
    var context: Context? = null;
    var inputReader : DevInputReader? = null
    val parser = MotionEventParser()
    private val gson = com.google.gson.Gson()
    private val callbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var motionJsonCallback: ((String) -> Unit)? = null

    constructor(context: Context) : this() {
        this.context = context;
    }

    private val TAG = this::class.simpleName


    override fun executeCommand(cmd: String?): String? {
        return try {
            val p = Runtime.getRuntime().exec(cmd)
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val out = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) {
                out.append(line).append('\n')
            }
            out.toString()
        } catch (e: Exception) {
            "Error: " + e.message
        }
    }

    override fun isRunningAsShell(): Boolean {
        return android.os.Process.myUid() == android.os.Process.SHELL_UID
    }

    override fun launchAppOnDisplay(packageName: String, displayId: Int): String {
        val cmpNm = ActivityHelper.getLauncherActivityComponentName(context!!, packageName)
        return if (cmpNm != null) ActivityHelper.launchActivityOnDisplayID(cmpNm, displayId)
        else "Error: No launcher activity found for package $packageName"
    }

    override fun grantPermissions(perms: List<String>): String {
        return PackageManagerHelper.grantPermissions(
            context?.packageName ?: "dev.legendsayantan.extendroid", perms, context!!.packageManager
        )
    }

    override fun getTopTenApps(): List<String> {
        return PackageManagerHelper.queryRecentAndFrequentApps(context!!)
    }

    override fun exitTasks(packageName: String): String {
        try {
            ActivityHelper.removePackageTask(context!!, packageName)
            return "success"
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }

    override fun dispatchKey(keyCode: Int, action: Int, displayID: Int, metaState: Int): String {
        return try {
            DisplayHelper.injectKeyEventToDisplay(displayID, action, keyCode, metaState)
            "success"
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun dispatch(event: MotionEvent, displayID: Int): String {
        return try {
            DisplayHelper.injectMotionEvent(event, displayID)
            "success"
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun setBuiltInDisplayPowerMode(mode: Int): String {
        return try {
            DisplayHelper.setDisplayPowerMode(mode)
            "success"
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun registerMotionEventListener(callback: IEventCallback): String{
        return try{

            inputReader = DevInputReader(listener = object : InputEventListener {
                override fun onEvent(event: InputEvent) {
                    val motionEvent = parser.feedEvent(event)
                    if (motionEvent != null) {
                        val json = gson.toJson(motionEvent)
                        // offload to executor so the input thread isn't blocked
                        val cb = callback
                        callbackExecutor.execute { cb.onMotionEvent(json) }
                    }
                }
                override fun onDeviceAdded(devicePath: String) { println("Device added: $devicePath") }
                override fun onDeviceRemoved(devicePath: String) { println("Device removed: $devicePath") }
                override fun onError(devicePath: String?, throwable: Throwable) {
                    System.err.println("Error ${devicePath ?: "scanner"}: ${throwable.message}")
                    throwable.printStackTrace()
                }
            })

            inputReader?.start()
            return "success"
        }catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun unregisterMotionEventListener(): String{
        return try{
            inputReader?.stop()
            inputReader = null
            callbackExecutor.shutdownNow()
            "success"
        }catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun goToSleep(): Boolean {
        return DisplayHelper.goToSleepRobust(context ?: return false)
    }

    override fun isDisplayActive(): Boolean{
        return DisplayHelper.isInteractive(context ?: return false)
    }

    override fun wakeUp(): Boolean {
        return DisplayHelper.wakeUpRobust(context ?: return false)
    }
}