package dev.legendsayantan.extendroid.services

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import dev.legendsayantan.extendroid.lib.ActivityHelper
import dev.legendsayantan.extendroid.lib.DisplayHelper
import dev.legendsayantan.extendroid.lib.PackageManagerHelper
import java.io.BufferedReader
import java.io.InputStreamReader


class RootService() : IRootService.Stub() {
    var context: Context? = null;

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

    override fun dispatchKey(keyCode: Int, action: Int, displayID: Int): String {
        return try {
            DisplayHelper.injectKeyEventToDisplay(displayID, action, keyCode, 0)
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
}