package dev.legendsayantan.extendroid

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * @author legendsayantan
 */
class Prefs(val context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
    fun changed(){
        changeListeners.forEach { it(context) }
    }

    fun registerChangeListener(listener:(callingContext:Context)->Unit){
        if(!changeListeners.contains(listener)) changeListeners.add(listener)
    }

    var floatingBall: Boolean
        get() = prefs.getBoolean("floatingBall",false)
        set(value){
            prefs.edit { putBoolean("floatingBall", value) }
            changed()
        }

    var collapseSeconds: Long
        get() = prefs.getLong("collapseSeconds",30L)
        set(value) {
            prefs.edit { putLong("collapseSeconds", value) }
            changed()
        }

    var densityAuto: Boolean
        get() = prefs.getBoolean("densityAuto",false)
        set(value) {
            prefs.edit { putBoolean("densityAuto", value) }
            changed()
        }

    var densityScale: Float
        get() = prefs.getFloat("densityScale",0.6f)
        set(value) {
            prefs.edit { putFloat("densityScale", value) }
            changed()
        }

    var backgroundDim: Float
        get() = prefs.getFloat("backgroundDim",0f)
        set(value) {
            prefs.edit { putFloat("backgroundDim", value) }
            changed()
        }


    companion object{
        val changeListeners: ArrayList<(Context)->Unit> = arrayListOf()
    }
}