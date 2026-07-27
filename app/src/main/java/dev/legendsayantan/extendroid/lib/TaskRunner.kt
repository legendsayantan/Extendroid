package dev.legendsayantan.extendroid.lib

import android.content.Context
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.SystemClock
import android.view.MotionEvent
import dev.legendsayantan.extendroid.Prefs
import dev.legendsayantan.extendroid.model.TaskData
import dev.legendsayantan.extendroid.services.IRootService
import java.util.Timer
import java.util.TreeMap
import kotlin.concurrent.timerTask

/**
 * TaskRunner recreates a virtual display to match task dimensions and injects recorded events.
 *
 * @author legendsayantan
 */
class TaskRunner(val ctx: Context) {
    private var isCancelled = false
    private var runnerThread: Thread? = null
    private var displayId = -1
    private var isHijacked = false
    private var wasAlreadyRunning = false
    private var dummyReader: ImageReader? = null
    var onCloseTab: (String) -> Unit = {}
    
    fun run(
        task: TaskData,
        svc: IRootService,
        onNeedNewTab: (String) -> Unit,
        onStarted: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!activeTasks.add(task.taskKey)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctx, "Task ${task.taskKey} is already running!", android.widget.Toast.LENGTH_SHORT).show()
            }
            onError("Task is already running")
            return
        }

        runnerThread = Thread {
            try {
                // Try to find an existing active display showing the app
                val mediaCore = dev.legendsayantan.extendroid.lib.MediaCore.mInstance
                if (mediaCore != null) {
                    for ((connId, params) in mediaCore.echoDisplayParams) {
                        val dId = params[0]
                        if (svc.getTopAppOnDisplay(dId) == task.pkgName) {
                            displayId = dId
                            isHijacked = true
                            
                            // Save original params and lock
                            mediaCore.originalDisplayParams[dId] = arrayOf(params[1], params[2], params[3])
                            mediaCore.lockedTaskDisplays.add(dId)
                            
                            // Resize display to task dimensions
                            svc.resizeVirtualDisplay(displayId, task.displayWidth, task.displayHeight, task.displayDpi)
                            break
                        }
                    }
                }

                if (!isHijacked) {
                    val mediaCore = dev.legendsayantan.extendroid.lib.MediaCore.mInstance
                    wasAlreadyRunning = mediaCore?.virtualDisplayIds?.containsKey(task.pkgName) == true

                    // Tell OverlayMenu to create a new preview tab
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onNeedNewTab(task.pkgName)
                    }

                    // Wait for the virtual display to become available
                    var waitCount = 0
                    while (waitCount < 50 && !isCancelled) {
                        val dId = dev.legendsayantan.extendroid.lib.MediaCore.mInstance?.virtualDisplayIds?.get(task.pkgName)
                        if (dId != null && dId != -1) {
                            displayId = dId
                            break
                        }
                        Thread.sleep(100)
                        waitCount++
                    }

                    if (displayId == -1) {
                        // Fallback: Create a headless display if UI tab failed
                        val metrics = ctx.resources.displayMetrics
                        val prefs = Prefs(ctx)
                        val density = if (prefs.densityAuto) {
                            (task.displayWidth * task.displayHeight * metrics.densityDpi * prefs.densityScale) / (metrics.heightPixels * metrics.widthPixels)
                        } else (metrics.densityDpi * prefs.densityScale)
                        
                        dummyReader = android.media.ImageReader.newInstance(task.displayWidth, task.displayHeight, android.graphics.PixelFormat.RGBA_8888, 1)
                        displayId = svc.createVirtualDisplay(
                            task.pkgName,
                            task.displayWidth,
                            task.displayHeight,
                            density.toInt(),
                            dummyReader!!.surface
                        )
                        if (displayId != -1) {
                            MediaCore.mInstance?.virtualDisplayIds?.put(task.pkgName, displayId)
                            MediaCore.mInstance?.virtualDisplayReady(task.pkgName, displayId)
                        } else {
                            cleanup(task, svc)
                            onError("Failed to create preview tab for virtual display")
                            return@Thread
                        }
                    }

                    // Resize to match the task dimensions precisely for local displays
                    svc.resizeVirtualDisplay(displayId, task.displayWidth, task.displayHeight, task.displayDpi)
                }
                onStarted()

                // 4. Merge timeline
                val timeline = TreeMap<Long, Any>()
                if (task.touches != null) timeline.putAll(task.touches)
                if (task.keyEvents != null) timeline.putAll(task.keyEvents)

            val baseUptime = SystemClock.uptimeMillis() + task.launchDelayMs
            val downTimeMap = mutableMapOf<Long, Long>()

            // 5. Run all events sequentially
            for ((relTime, eventObj) in timeline) {
                if (isCancelled) break
                val execTime = baseUptime + relTime
                val delay = execTime - SystemClock.uptimeMillis()
                
                if (delay > 0) {
                    try {
                        Thread.sleep(delay)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                if (isCancelled) break
                
                try {
                    when (eventObj) {
                        is SerializableMotionEvent -> {
                            val recordedDownTime = eventObj.downTime
                            if (!downTimeMap.containsKey(recordedDownTime)) {
                                downTimeMap[recordedDownTime] = execTime
                            }
                            val rebasedDownTime = downTimeMap[recordedDownTime]!!
                            val motionEvent = eventObj.copy(
                                downTime = rebasedDownTime, 
                                eventTime = execTime
                            ).toMotionEvent()
                            
                            svc.dispatch(motionEvent, displayId)
                            motionEvent.recycle()
                        }
                        is SerializableKeyEvent -> {
                            if (eventObj.keyCode != 0) {
                                svc.dispatchKey(eventObj.keyCode, eventObj.action, displayId, eventObj.metaState)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 6. Post-task cleanup
            if (!isCancelled) {
                try {
                    Thread.sleep(300L)
                } catch (e: InterruptedException) {}
            }
            
            if (!isCancelled) {
                cleanup(task, svc)
                onDone()
            }

        } catch (e: InterruptedException) {
            // Silently swallow interruption caused by cancel()
        } catch (e: Exception) {
            e.printStackTrace()
            cancel(task, svc)
            onError(e.message ?: "Unknown error")
        }
        }
        runnerThread?.start()
    }

    private fun cleanup(task: TaskData, svc: IRootService) {
        val mediaCore = dev.legendsayantan.extendroid.lib.MediaCore.mInstance
        if (isHijacked && mediaCore != null) {
            // The unlock bookkeeping below must run no matter what happens above - previously,
            // if restoring the display's size/content threw (e.g. the root service call failed),
            // the display was left in mediaCore.lockedTaskDisplays forever, silently wedging
            // every future RunApp/Resize for that session with no error surfaced to the user.
            try {
                val req = mediaCore.queuedDisplayRequests[displayId]

                // Check app launch
                val appToLaunch = req?.launchAppPkg

                // Apply scale / resize OR restore original
                try {
                    if (req?.resizeWidth != null && req.resizeHeight != null) {
                        val newDpi = dev.legendsayantan.extendroid.echo.RemoteSessionHandler.computedDensity(ctx, req.resizeWidth!!, req.resizeHeight!!, req.scale ?: 1f)
                        svc.resizeVirtualDisplay(displayId, req.resizeWidth!!, req.resizeHeight!!, newDpi)
                        val connId = mediaCore.echoDisplayParams.entries.find { it.value[0] == displayId }?.key
                        if (connId != null) {
                            mediaCore.echoDisplayParams[connId] = arrayOf(displayId, req.resizeWidth!!, req.resizeHeight!!, newDpi)
                            mediaCore.sessionCapturerResizers[connId]?.invoke(req.resizeWidth!!, req.resizeHeight!!, newDpi)
                        }
                    } else {
                        // Restore original
                        val orig = mediaCore.originalDisplayParams[displayId]
                        if (orig != null) {
                            svc.resizeVirtualDisplay(displayId, orig[0], orig[1], orig[2])
                        }
                    }
                } catch (e: Exception) {
                    Logging(ctx).e(e, "TaskRunner.cleanup.resize")
                }

                // Launch requested app if any
                if (appToLaunch != null) {
                    try {
                        svc.launchAppOnDisplay(appToLaunch, displayId)
                        // We update history so web UI knows the new app
                        val connId = mediaCore.echoDisplayParams.entries.find { it.value[0] == displayId }?.key
                        if (connId != null) {
                            mediaCore.appRemoteAccessHistory[connId]?.let {
                                if (!it.contains(appToLaunch)) {
                                    mediaCore.appRemoteAccessHistory[connId] = it + appToLaunch
                                }
                            } ?: run {
                                mediaCore.appRemoteAccessHistory[connId] = listOf(appToLaunch)
                            }
                        }
                    } catch (e: Exception) {
                        Logging(ctx).e(e, "TaskRunner.cleanup.launchApp")
                    }
                }
            } finally {
                // Unlock - always, even if restoring the display above failed.
                mediaCore.lockedTaskDisplays.remove(displayId)
                mediaCore.originalDisplayParams.remove(displayId)
                mediaCore.queuedDisplayRequests.remove(displayId)
            }
        } else {
            if (task.stopAfter && !wasAlreadyRunning) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onCloseTab(task.pkgName)
                }
            }
            // Do not destroy the virtual display manually here, let MediaCore manage its lifecycle
            dummyReader?.close()
            dummyReader = null
        }
        activeTasks.remove(task.taskKey)
    }

    fun cancel(task: TaskData? = null, svc: IRootService? = null) {
        isCancelled = true
        runnerThread?.interrupt()
        runnerThread = null
        if (task != null && svc != null) {
            cleanup(task, svc)
        } else {
            dummyReader?.close()
            dummyReader = null
        }
    }

    companion object {
        val activeTasks = java.util.concurrent.CopyOnWriteArraySet<String>()
    }
}
