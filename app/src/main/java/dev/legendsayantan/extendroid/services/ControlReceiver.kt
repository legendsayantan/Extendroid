package dev.legendsayantan.extendroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.legendsayantan.extendroid.MainActivity
import dev.legendsayantan.extendroid.lib.ShizukuActions.Companion.setMainDisplayPowerMode
import dev.legendsayantan.extendroid.lib.Utils.Companion.getLaunchableApps
import java.io.File

/**
 * @author legendsayantan
 * @property ControlReceiver
 * Controls the operations of ExtendService programmatically. data is delimited by ";"
 * @param CREATE data:
 *  packageName (String), width(Int), height(Int), density(Int), helper(Boolean), window_mode(POPUP/WIRELESS/BOTH)
 * @param DELETE data:
 *  window_id(Int), kill_app(Boolean)
 */
class ControlReceiver : BroadcastReceiver() {
    val STATUS_FILE = "status.txt"
    val ACTIVE_APPS_FILE = "active_apps.txt"
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val statusFile = File(context.getExternalFilesDir(null), STATUS_FILE)
        val activeAppsFile = File(context.getExternalFilesDir(null), ACTIVE_APPS_FILE)
        val info = intent.getStringExtra("data")?.split(",")?: listOf()
        try {
            when (action) {
                "${context.packageName}.GET_STATUS" -> {
                    val windows = ExtendService.queryWindows()
                    statusFile.writeText((if (ExtendService.running) 1 else 0).toString())
                    activeAppsFile.writeText(windows.joinToString("\n") {
                        arrayOf(it.id.toString(),
                            context.packageManager.getApplicationInfo(it.pkg, 0)
                                .loadLabel(context.packageManager),
                            it.windowInfo
                        ).joinToString(";")
                    })
                }

                "${context.packageName}.CREATE" -> {
                    ExtendService.onAttachWindow(
                        info[0],
                        Pair(info[1].toInt(), info[2].toInt()),
                        info[3].toInt(),
                        info[4].toBoolean(),
                        ExtendService.Companion.WindowMode.valueOf(info[5].uppercase()),
                        true
                    ) {
                        MainActivity.refreshStatus()
                    }
                }

                "${context.packageName}.DELETE" -> {
                    ExtendService.onDetachWindow(info[0].toInt(), info[1].toBoolean())
                    MainActivity.refreshStatus()
                }

                "${context.packageName}.STOP" -> {
                    context.stopService(Intent(context, ExtendService::class.java))
                    MainActivity.refreshStatus()
                }

                "${context.packageName}.LOCK" -> {
                    context.setMainDisplayPowerMode(0);
                }
            }
        } catch (_: Exception) {
        }
    }
}