package dev.legendsayantan.extendroid.echo

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import dev.legendsayantan.extendroid.IEventCallback
import dev.legendsayantan.extendroid.echo.RemoteSessionHandler.MotionEventData
import dev.legendsayantan.extendroid.lib.Logging
import dev.legendsayantan.extendroid.services.IRootService
import java.util.*
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
        val scaling = determineScaling(svc)
        Logging(ctx).d("Unlocking device with hardware scaling -> $scaling","RemoteUnlocker")
        unlockData.forEach { eventData ->
            val timeToRun = now + eventData.eventTime
            eventData.downTime += uptimeMillis;
            eventData.eventTime += uptimeMillis;
            val motionEvent = RemoteSessionHandler.createMotionEventFromData(eventData,scaling)
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

        fun determineScaling(svc: IRootService): Pair<Float, Float> {
            // run getevent -p and wm size
            val geteventOut = try {
                svc.executeCommand("getevent -p")
            } catch (e: Exception) {
                return Pair(1f, 1f)
            }

            val wmOut = try {
                svc.executeCommand("wm size")
            } catch (e: Exception) {
                return Pair(1f, 1f)
            }

            // Regex to extract device blocks:
            val deviceBlockRe = Regex(
                "(?m)^add device (\\d+):\\s*(/dev/input/event\\d+)?\\s*(.*?)(?=^add device \\d+:|\\z)",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
            )

            // Regex to extract lines like: 0035  : value 0, min 0, max 12599, ...
            val absLineRe = Regex("\\b([0-9a-fA-F]{2,4})\\s*:\\s*value\\s*-?\\d+\\s*,\\s*min\\s*(-?\\d+)\\s*,\\s*max\\s*(-?\\d+)",
                RegexOption.IGNORE_CASE)

            data class Candidate(val devPath: String?, val name: String?, val hasDirect: Boolean,
                                 val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int)

            val candidates = mutableListOf<Candidate>()

            for (m in deviceBlockRe.findAll(geteventOut)) {
                val devIndex = m.groupValues[1]
                val devPath = m.groupValues.getOrNull(2)
                val block = m.groupValues.getOrNull(3) ?: continue

                // optional: extract "name" line inside block
                val nameRe = Regex("""(?m)^\s*name:\s*"([^"]+)"""")
                val nameMatch = nameRe.find(block)
                val devName = nameMatch?.groupValues?.get(1)

                val hasDirect = block.contains("INPUT_PROP_DIRECT", ignoreCase = true)

                var xMin: Int? = null
                var xMax: Int? = null
                var yMin: Int? = null
                var yMax: Int? = null

                for (absM in absLineRe.findAll(block)) {
                    val hex = absM.groupValues[1].lowercase(Locale.ROOT).padStart(4, '0')
                    val min = absM.groupValues[2].toIntOrNull() ?: continue
                    val max = absM.groupValues[3].toIntOrNull() ?: continue
                    when (hex) {
                        "0035" -> { xMin = min; xMax = max } // ABS_MT_POSITION_X
                        "0036" -> { yMin = min; yMax = max } // ABS_MT_POSITION_Y
                    }
                }

                if (xMin != null && xMax != null && yMin != null && yMax != null) {
                    candidates.add(Candidate(devPath, devName, hasDirect, xMin, xMax, yMin, yMax))
                }
            }

            // Choose best candidate:
            // 1) prefer hasDirect == true
            // 2) else pick candidate with largest (xMax + yMax) as heuristic for touch panel
            val chosen = when {
                candidates.isEmpty() -> null
                candidates.any { it.hasDirect } -> candidates.first { it.hasDirect }
                else -> candidates.maxByOrNull { (it.xMax.toLong() + it.yMax.toLong()) }
            }

            if (chosen == null) return Pair(1f, 1f)

            // parse screen size
            val sizeRe = Regex("""Physical size:\s*(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
            val sizeMatch = sizeRe.find(wmOut)
            val screenW = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
            val screenH = sizeMatch?.groupValues?.get(2)?.toIntOrNull()

            if (screenW == null || screenH == null) return Pair(1f, 1f)

            val rangeX = (chosen.xMax - chosen.xMin).toFloat()
            val rangeY = (chosen.yMax - chosen.yMin).toFloat()
            val scaleX = if (screenW > 0 && rangeX > 0) screenW / rangeX else 1f
            val scaleY = if (screenH > 0 && rangeY > 0) screenH / rangeY else 1f

            // debug log (optional)
            Log.i("SCALING", "picked device=${chosen.devPath} name=${chosen.name} x=${chosen.xMin}..${chosen.xMax} y=${chosen.yMin}..${chosen.yMax} -> scaleX=$scaleX scaleY=$scaleY")

            return Pair(scaleX, scaleY)
        }

    }
}