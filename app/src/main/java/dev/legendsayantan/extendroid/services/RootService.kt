package dev.legendsayantan.extendroid.services

import android.content.Context
import android.view.MotionEvent
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
    lateinit var inputReader : DevInputReader
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

    override fun registerMotionEventListener(): String{
        return try{
            inputReader = DevInputReader(listener = object : InputEventListener {
                override fun onEvent(event: InputEvent) {
                    // This will be called on a background thread.
                    println("Got event from ${event.devicePath}: type=${event.type}, code=${event.code}, value=${event.value}, time=${event.sec}.${event.usec}")
                    // Do NOT touch UI components here.
                }

                override fun onDeviceAdded(devicePath: String) {
                    println("Device added: $devicePath")
                }

                override fun onDeviceRemoved(devicePath: String) {
                    println("Device removed: $devicePath")
                }

                override fun onError(devicePath: String?, throwable: Throwable) {
                    print(throwable.stackTraceToString())
                    System.err.println("Error ${devicePath ?: "scanner"}: ${throwable.message}")
                }
            })

// start reading (spawns background threads)
            inputReader.start()

            "success"
        }catch (e: Exception) {
            e.stackTraceToString()
        }
    }

    override fun unregisterMotionEventListener(): String{
        return try{
            inputReader.stop()
            "success"
        }catch (e: Exception) {
            e.stackTraceToString()
        }
    }
}