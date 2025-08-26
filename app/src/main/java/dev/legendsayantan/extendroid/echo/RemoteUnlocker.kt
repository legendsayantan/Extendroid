package dev.legendsayantan.extendroid.echo

import android.content.Context
import com.google.gson.Gson

/**
 * @author legendsayantan
 */
class RemoteUnlocker(val ctx: Context) {

    var unlockData : Array<RemoteSessionHandler.MotionEventData>
        get(){
            return try {
                val file = ctx.getFileStreamPath(FILENAME)
                if (!file.exists() || file.length() == 0L) {
                    emptyArray()
                } else {
                    ctx.openFileInput(FILENAME).use { input ->
                        val jsonText = input.bufferedReader().readText()
                        if (jsonText.isBlank()) {
                            emptyArray()
                        } else {
                            Gson().fromJson(jsonText, object : com.google.gson.reflect.TypeToken<Array<RemoteSessionHandler.MotionEventData>>() {}.type)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyArray()
            }
        }
        set(value) {
            val jsonText = Gson().toJson(value)
            ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { output ->
                output.write(jsonText.toByteArray())
            }
        }

    fun startTraining(onComplete:()->Unit) {

    }
    companion object{
        const val FILENAME = "remote_unlock.json"
    }
}