package dev.legendsayantan.extendroid

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * @author legendsayantan
 */
class Prefs(val context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    val echo : SharedPreferences = context.getSharedPreferences("echo", Context.MODE_PRIVATE)

    fun configChanged(){
        configChangeListeners.forEach { it(context) }
    }

    fun echoChanged(){
        echoChangeListeners.forEach { it(context) }
    }

    fun registerConfigChangeListener(listener:(callingContext:Context)->Unit){
        if(!configChangeListeners.contains(listener)) configChangeListeners.add(listener)
    }

    fun unregisterConfigChangeListener(listener:(callingContext:Context)->Unit){
        configChangeListeners.remove(listener)
    }

    fun registerEchoChangeListener(listener:(callingContext:Context)->Unit){
        if(!echoChangeListeners.contains(listener)) echoChangeListeners.add(listener)
    }

    fun unregisterEchoChangeListener(listener:(callingContext:Context)->Unit){
        echoChangeListeners.remove(listener)
    }

    var allowedMiuiPerms : Boolean
        get() = prefs.getBoolean("allowedMiuiPerms", false)
        set(value) {
            prefs.edit { putBoolean("allowedMiuiPerms", value) }
            configChanged()
        }

    var floatingBall: Boolean
        get() = prefs.getBoolean("floatingBall",false)
        set(value){
            prefs.edit { putBoolean("floatingBall", value) }
            configChanged()
        }

    var collapseSeconds: Long
        get() = prefs.getLong("collapseSeconds",30L)
        set(value) {
            prefs.edit { putLong("collapseSeconds", value) }
            configChanged()
        }

    var densityAuto: Boolean
        get() = prefs.getBoolean("densityAuto",false)
        set(value) {
            prefs.edit { putBoolean("densityAuto", value) }
            configChanged()
        }

    var densityScale: Float
        get() = prefs.getFloat("densityScale",0.6f)
        set(value) {
            prefs.edit { putFloat("densityScale", value) }
            configChanged()
        }

    var backgroundDim: Float
        get() = prefs.getFloat("backgroundDim",0f)
        set(value) {
            prefs.edit { putFloat("backgroundDim", value) }
            configChanged()
        }

    var fcmSent : Boolean
        get() = echo.getBoolean("fcmSent",false)
        set(value) {
            echo.edit { putBoolean("fcmSent",value) }
            echoChanged()
        }

    var balance: Float
        get() = echo.getFloat("balance",3f)
        set(value) {
            echo.edit { putFloat("balance", value) }
            configChanged()
        }

    var nextSyncTime : Long
        get() = echo.getLong("nextSyncTime",0L)
        set(value) {
            echo.edit { putLong("nextSyncTime", value) }
            echoChanged()
        }

    var lowQuality: Boolean
        get() = echo.getBoolean("lowQuality",false)
        set(value) {
            echo.edit { putBoolean("lowQuality",value) }
            echoChanged()
        }

    var autoPurchase: Boolean
        get() = echo.getBoolean("autoPurchase",false)
        set(value) {
            echo.edit { putBoolean("autoPurchase",value) }
            echoChanged()
        }



    companion object{
        val configChangeListeners: ArrayList<(Context)->Unit> = arrayListOf()
        val echoChangeListeners: ArrayList<(Context)->Unit> = arrayListOf()
    }
}