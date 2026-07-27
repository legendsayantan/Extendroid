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
    private val activeTimers = mutableListOf<Timer>()

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
        activeTimers.add(timer)
        var wasLocked = false
        var startDownTime = 0L
        var startEventTime = 0L
        timer.schedule(timerTask {
            if(wasLocked != keyguardManager.isKeyguardLocked){
                if (keyguardManager.isKeyguardLocked) {
                    println("RMEL")
                    val motionCallback = object : IEventCallback.Stub() {
                        override fun onMotionEvent(json: String) {
                            // Do not log the raw payload here: it's the literal recorded
                            // lockscreen-unlock gesture (coordinates/timing) being trained.
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
                            activeTimers.remove(timer)
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
        // Every Unlock packet used to spawn its own independent, unsynchronized timer/thread.
        // Firing it repeatedly in quick succession (e.g. rapid remote lock/unlock cycles) let
        // multiple scheduled touch-event sequences run concurrently and interleave onto the same
        // display - a DOWN from one attempt landing between the MOVE/UP of another - which
        // corrupts Android's touch dispatch state (stuck pointer), stalling input system-wide and
        // freezing the mirrored frame. Only one unlock sequence is allowed in flight at a time;
        // overlapping requests are dropped instead of interleaved.
        if (!unlockInProgress.compareAndSet(false, true)) {
            Logging(ctx).i("Ignoring Unlock request - one is already in progress", "RemoteUnlocker")
            return
        }

        val timer = Timer()
        activeTimers.add(timer)

        // Cleanup must run no matter how this unlock sequence ends, otherwise a single failure
        // (e.g. the last dispatch throwing) would leak the Timer's background thread forever and
        // permanently wedge unlockInProgress, blocking every future unlock attempt.
        fun finish() {
            try {
                timer.cancel()
            } catch (_: Exception) {
            }
            activeTimers.remove(timer)
            unlockInProgress.set(false)
        }

        try {
            svc.wakeUp();
            val handler = Handler(ctx.mainLooper)
            val scaling = determineScaling(svc) // This can take 500ms-1500ms to run shell commands
            if (scaling == null) {
                Logging(ctx).e("Could not determine touchscreen scaling; aborting unlock attempt instead of guessing", "RemoteUnlocker")
                finish()
                return
            }
            val now = System.currentTimeMillis()+1000;
            val uptimeMillis = SystemClock.uptimeMillis()+1000;
            Logging(ctx).d("Unlocking device with hardware scaling -> $scaling","RemoteUnlocker")

            val data = unlockData
            if (data.isEmpty()) {
                finish()
                return
            }
            val lastIndex = data.size - 1
            data.forEachIndexed { index, eventData ->
                val timeToRun = now + eventData.eventTime
                eventData.downTime += uptimeMillis;
                eventData.eventTime += uptimeMillis;
                val motionEvent = RemoteSessionHandler.createMotionEventFromData(eventData,scaling)
                timer.schedule(timerTask {
                    handler.post {
                        try {
                            svc.dispatch(motionEvent,0)
                        } catch (e: Exception) {
                            Logging(ctx).e(e, "RemoteUnlocker")
                        } finally {
                            if (index == lastIndex) finish()
                        }
                    }
                }, Date(timeToRun))
            }
        } catch (e: Exception) {
            Logging(ctx).e(e, "RemoteUnlocker")
            finish()
        }
    }

    fun cleanup() {
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()
    }

    companion object {
        const val FILENAME = "remote_unlock.json"

        // Shared across every RemoteUnlocker instance (a new one is created per Unlock packet)
        // so overlapping unlock() calls can be detected and dropped instead of interleaving.
        private val unlockInProgress = java.util.concurrent.atomic.AtomicBoolean(false)


        fun isScreenLocked(context: Context): Boolean {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguardManager.isKeyguardLocked
        }

        // Returns null (instead of a guessed 1:1 scale) when the touchscreen's raw coordinate
        // range or the screen size can't be determined - replaying an unlock gesture with a
        // wrong scale would tap the wrong spots, and repeated wrong PIN/pattern attempts risk
        // tripping Android's own attempt-limit lockout. Better to abort than to guess.
        fun determineScaling(svc: IRootService): Pair<Float, Float>? {
            // run getevent -p and wm size
            val geteventOut = try {
                svc.executeCommand("getevent -p")
            } catch (e: Exception) {
                Log.e("RemoteUnlocker", "determineScaling: 'getevent -p' failed: ${e.message}")
                return null
            }

            val wmOut = try {
                svc.executeCommand("wm size")
            } catch (e: Exception) {
                Log.e("RemoteUnlocker", "determineScaling: 'wm size' failed: ${e.message}")
                return null
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

            if (chosen == null) {
                Log.e("RemoteUnlocker", "determineScaling: no candidate touchscreen input device found")
                return null
            }

            // parse screen size
            val sizeRe = Regex("""Physical size:\s*(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
            val sizeMatch = sizeRe.find(wmOut)
            val screenW = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
            val screenH = sizeMatch?.groupValues?.get(2)?.toIntOrNull()

            if (screenW == null || screenH == null) {
                Log.e("RemoteUnlocker", "determineScaling: could not parse 'wm size' output")
                return null
            }

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