package dev.legendsayantan.extendroid.echo

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import com.google.gson.Gson
import dev.legendsayantan.extendroid.IEventCallback
import dev.legendsayantan.extendroid.echo.RemoteSessionHandler.MotionEventData
import dev.legendsayantan.extendroid.services.IRootService
import java.util.Date
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
class RemoteUnlocker(val ctx: Context) {

    val gson = Gson()
    var unlockData: Array<RemoteSessionHandler.MotionEventData>
        get() {
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
                            gson.fromJson(jsonText,
                                object :
                                    com.google.gson.reflect.TypeToken<Array<MotionEventData>>() {}.type
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyArray()
            }
        }
        set(value) {
            val jsonText = gson.toJson(value)
            ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { output ->
                output.write(jsonText.toByteArray())
            }
        }

    fun startTraining(svc: IRootService, onComplete: (success:Boolean) -> Unit) {
        val trainingData: ArrayList<RemoteSessionHandler.MotionEventData> = arrayListOf()
        val keyguardManager =
            ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val timer = Timer()
        var wasLocked = false
        var startDownTime = 0L
        var startEventTime = 0L
        timer.schedule(timerTask {
            if(wasLocked != keyguardManager.isKeyguardLocked){
                if (keyguardManager.isKeyguardLocked) {
                    println("RMEL")
                    val motionCallback = object : IEventCallback.Stub() {
                        override fun onMotionEvent(json: String) {
                            println("Client got: $json")
                            val motionEventData = gson.fromJson(json, MotionEventData::class.java)
                            if(startDownTime == 0L){
                                startDownTime = motionEventData.downTime
                                startEventTime = motionEventData.eventTime
                            }
                            motionEventData.downTime -= startDownTime
                            motionEventData.eventTime -= startEventTime
                            trainingData.add(motionEventData)
                        }
                    }
                    println("Register " + svc.registerMotionEventListener(motionCallback))
                    svc.wakeUp()
                } else {
                    try {
                        println("Unregister " + svc.unregisterMotionEventListener())
                        onComplete(trainingData.isNotEmpty())
                        if(trainingData.isNotEmpty()){
                            unlockData = trainingData.toTypedArray()
                        }
                    } catch (t: Throwable) {
                        System.err.println(t.stackTraceToString())
                    } finally {
                        try {
                            timer.cancel()
                        } catch (e: Exception) {
                            System.err.println(e.stackTraceToString())
                        }
                    }
                }
                wasLocked = keyguardManager.isKeyguardLocked
            }
        },2000,1000)
        val a = svc.goToSleep()
        if (!a) {
            println("ERROR")
        }
    }

    fun testUnlock(svc: IRootService){
        svc.goToSleep()
        Handler(ctx.mainLooper).postDelayed({
            unlock(svc)
        },2000)
    }

    fun unlock(svc: IRootService){
        svc.wakeUp();
        val timer = Timer()
        val handler = Handler(ctx.mainLooper)
        val now = System.currentTimeMillis()+1000;
        val uptimeMillis = SystemClock.uptimeMillis()+1000;
        unlockData.forEach { eventData ->
            val timeToRun = now + eventData.eventTime
            eventData.downTime += uptimeMillis;
            eventData.eventTime += uptimeMillis;
            val motionEvent = RemoteSessionHandler.createMotionEventFromData(eventData)
            timer.schedule(timerTask {
                println("posting $eventData")
                handler.post { svc.dispatch(motionEvent,0) }
            }, Date(timeToRun))
        }
    }

    companion object {
        const val FILENAME = "remote_unlock.json"


        fun isScreenLocked(context: Context): Boolean {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguardManager.isKeyguardLocked
        }
    }
}