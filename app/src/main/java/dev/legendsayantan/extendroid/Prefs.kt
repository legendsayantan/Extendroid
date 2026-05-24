package dev.legendsayantan.extendroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.lang.ref.WeakReference
import androidx.core.content.edit
import dev.legendsayantan.extendroid.lib.Logging
import org.json.JSONArray
import org.json.JSONObject

/**
 * @author legendsayantan
 */
class Prefs(val context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    val echo: SharedPreferences = context.getSharedPreferences("echo", Context.MODE_PRIVATE)

    fun configChanged() {
        configChangeListeners.iterator().let { iterator ->
            while (iterator.hasNext()) {
                val listener = iterator.next().get()
                if (listener != null) listener(context)
                else iterator.remove()
            }
        }
    }

    fun echoChanged() {
        echoChangeListeners.iterator().let { iterator ->
            while (iterator.hasNext()) {
                val listener = iterator.next().get()
                if (listener != null) listener(context)
                else iterator.remove()
            }
        }
    }

    fun registerConfigChangeListener(listener: (callingContext: Context) -> Unit) {
        if (configChangeListeners.none { it.get() == listener }) {
            configChangeListeners.add(WeakReference(listener))
        }
    }

    fun unregisterConfigChangeListener(listener: (callingContext: Context) -> Unit) {
        val iterator = configChangeListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            if (ref.get() == listener || ref.get() == null) {
                iterator.remove()
            }
        }
    }

    fun registerEchoChangeListener(listener: (callingContext: Context) -> Unit) {
        if (echoChangeListeners.none { it.get() == listener }) {
            echoChangeListeners.add(WeakReference(listener))
        }
    }

    fun unregisterEchoChangeListener(listener: (callingContext: Context) -> Unit) {
        val iterator = echoChangeListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            if (ref.get() == listener || ref.get() == null) {
                iterator.remove()
            }
        }
    }

    var allowedMiuiPerms: Boolean
        get() = prefs.getBoolean("allowedMiuiPerms", false)
        set(value) {
            prefs.edit { putBoolean("allowedMiuiPerms", value) }
            configChanged()
        }

    var floatingBall: Boolean
        get() = prefs.getBoolean("floatingBall", false)
        set(value) {
            prefs.edit { putBoolean("floatingBall", value) }
            configChanged()
        }

    var collapseSeconds: Long
        get() = prefs.getLong("collapseSeconds", 30L)
        set(value) {
            prefs.edit { putLong("collapseSeconds", value) }
            configChanged()
        }

    var densityAuto: Boolean
        get() = prefs.getBoolean("densityAuto", false)
        set(value) {
            prefs.edit { putBoolean("densityAuto", value) }
            configChanged()
        }

    var densityScale: Float
        get() = prefs.getFloat("densityScale", 0.6f)
        set(value) {
            prefs.edit { putFloat("densityScale", value) }
            configChanged()
        }

    var backgroundDim: Float
        get() = prefs.getFloat("backgroundDim", 0f)
        set(value) {
            prefs.edit { putFloat("backgroundDim", value) }
            configChanged()
        }

    var pinnedApps: Set<String>
        get() = prefs.getStringSet("pinnedApps", setOf()) ?: setOf()
        set(value) {
            prefs.edit { putStringSet("pinnedApps", value) }
            configChanged()
        }

    var fcmSent: Boolean
        get() = echo.getBoolean("fcmSent", false)
        set(value) {
            echo.edit { putBoolean("fcmSent", value) }
            echoChanged()
        }

    var balance: Float
        get() = echo.getFloat("balance", 5f)
        set(value) {
            echo.edit { putFloat("balance", value) }
            echoChanged()
        }

    var nextSyncTime: Long
        get() = echo.getLong("nextSyncTime", 0L)
        set(value) {
            echo.edit { putLong("nextSyncTime", value) }
            echoChanged()
        }

    var deviceName: String
        get() = prefs.getString("deviceName", "") ?: ""
        set(value) {
            prefs.edit { putString("deviceName", value) }
            echoChanged()
        }

    val echoMappings: Map<String, String>
        get() {
            /*
             * Example data:
             * {
             * "0": "InstalledApps",
             * "1": "RunningApps",
             * "2": "RunApp"
             * }
             */
            val jsonString = echo.getString("mappings", "[]") ?: "[]"
            return try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().asSequence().associateWith { jsonObject.getString(it) }
            } catch (e: Exception) {
                Logging(context).e(e,"Prefs")
                emptyMap()
            }
        }

    val lastMappingsLoaded: Long
        get() = echo.getLong("lastMappingsLoaded", 0L)

    fun setEchoMappings(string: String) {
        echo.edit {
            putString("mappings", string)
            putLong("lastMappingsLoaded", System.currentTimeMillis())
        }
    }

    var disclaimerTextShown : Boolean
        get() = echo.getBoolean("disclaimerTextShown", false)
        set(value) {
            echo.edit { putBoolean("disclaimerTextShown", value) }
        }

    var echoBlackList : Set<String>
        get() = echo.getStringSet("blacklist",setOf<String>())?: setOf<String>()
        set(value) {
            echo.edit { putStringSet("blacklist",value) }
        }

    var existingAccounts : Set<String>
        get() = echo.getStringSet("existingAccounts",setOf<String>()) ?: setOf<String>()
        set(value) {
            echo.edit { putStringSet("existingAccounts",value) }
        }



    companion object {
        val configChangeListeners: ArrayList<WeakReference<(Context) -> Unit>> = arrayListOf()
        val echoChangeListeners: ArrayList<WeakReference<(Context) -> Unit>> = arrayListOf()
    }
}