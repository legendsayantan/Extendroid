package dev.legendsayantan.extendroid.lib

import android.content.Context
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.SystemClock
import android.view.MotionEvent
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
    private val activeTimers = mutableListOf<Timer>()
    private var isCancelled = false
    private var displayId = -1
    private var isHijacked = false
    private var dummyReader: ImageReader? = null
    
    fun run(
        task: TaskData,
        svc: IRootService,
        onNeedNewTab: (String) -> Unit,
        onStarted: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Thread {
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
                    // Tell OverlayMenu to create a new preview tab
                    onNeedNewTab(task.pkgName)

                    // Wait for the virtual display to become available
                    var waitCount = 0
                    while (waitCount < 50) {
                        val dId = dev.legendsayantan.extendroid.lib.MediaCore.mInstance?.virtualDisplayIds?.get(task.pkgName)
                        if (dId != null && dId != -1) {
                            displayId = dId
                            break
                        }
                        Thread.sleep(100)
                        waitCount++
                    }

                    if (displayId == -1) {
                        cleanup(task, svc)
                        onError("Failed to create preview tab for virtual display")
                        return@Thread
                    }
                }
                onStarted()

                // 4. Merge timeline
                val timeline = TreeMap<Long, Any>()
            timeline.putAll(task.touches)
            timeline.putAll(task.keyEvents)

            val baseUptime = SystemClock.uptimeMillis() + task.launchDelayMs
            val downTimeMap = mutableMapOf<Long, Long>()

            // 5. Schedule all events
            for ((relTime, eventObj) in timeline) {
                val execTime = baseUptime + relTime
                val delay = execTime - SystemClock.uptimeMillis()
                
                val timer = Timer()
                activeTimers.add(timer)
                timer.schedule(timerTask {
                    if (isCancelled) return@timerTask
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
                                svc.dispatchKey(eventObj.keyCode, eventObj.action, displayId, eventObj.metaState)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, maxOf(0L, delay))
            }

            // 6. Post-task cleanup
            val lastEventTime = if (timeline.isEmpty()) 0L else timeline.lastKey()
            val finishDelay = task.launchDelayMs + lastEventTime + 300L
            
            val finishTimer = Timer()
            activeTimers.add(finishTimer)
            finishTimer.schedule(timerTask {
                if (isCancelled) return@timerTask
                cleanup(task, svc)
                onDone()
            }, finishDelay)

        } catch (e: Exception) {
            e.printStackTrace()
            cancel(task, svc)
            onError(e.message ?: "Unknown error")
        }
        }.start()
    }

    private fun cleanup(task: TaskData, svc: IRootService) {
        val mediaCore = dev.legendsayantan.extendroid.lib.MediaCore.mInstance
        if (isHijacked && mediaCore != null) {
            val req = mediaCore.queuedDisplayRequests[displayId]
            
            // Check app launch
            val appToLaunch = req?.launchAppPkg
            
            // Apply scale / resize OR restore original
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
            
            // Launch requested app if any
            if (appToLaunch != null) {
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
            }
            
            // Unlock
            mediaCore.lockedTaskDisplays.remove(displayId)
            mediaCore.originalDisplayParams.remove(displayId)
            mediaCore.queuedDisplayRequests.remove(displayId)
            
        } else {
            if (task.stopAfter) {
                svc.exitTasks(task.pkgName)
            }
            if (displayId != -1) {
                svc.destroyVirtualDisplay(displayId)
                displayId = -1
            }
            dummyReader?.close()
            dummyReader = null
        }
    }

    fun cancel(task: TaskData? = null, svc: IRootService? = null) {
        isCancelled = true
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()
        if (task != null && svc != null) {
            cleanup(task, svc)
        } else {
            dummyReader?.close()
            dummyReader = null
        }
    }
}
