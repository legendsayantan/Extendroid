package dev.legendsayantan.extendroid.echo


import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import dev.legendsayantan.extendroid.echo.EchoNetworkUtils.Companion.mappings
import dev.legendsayantan.extendroid.lib.MapKeyEvent
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.lib.PackageManagerHelper
import dev.legendsayantan.extendroid.services.IRootService
import org.json.JSONObject
import org.webrtc.DataChannel
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.math.roundToInt

/**
 * @author legendsayantan
 */
class RemoteSessionHandler {
    companion object {

        fun handleDataChannel(
            ctx: Context,
            mediaCore: MediaCore,
            dataChannel: DataChannel,
            noDisplay: Boolean
        ) {
            when (dataChannel.state()) {
                DataChannel.State.CLOSED, DataChannel.State.CLOSING -> {
                    //do nothing
                }

                DataChannel.State.CONNECTING -> {
                    //do nothing
                }

                DataChannel.State.OPEN -> {
                    if(!noDisplay){
                        Thread {
                            val allAppsMap =
                                PackageManagerHelper.getLaunchableApps(ctx.packageManager)
                                    .associate {
                                        it.packageName to it.appName
                                    }
                            val appListJson = Gson().toJson(allAppsMap)
                            dataChannel.send(
                                createDataChannelPacket(appListJson, PacketType.InstalledApps)
                            )
                        }.start()
                    }
                }
            }

            mediaCore.onRunningRemoteAppsUpdate = {
                val runningApps = Gson().toJson(mediaCore.appRemoteAccessHistory[it])
                dataChannel.send(
                    createDataChannelPacket(runningApps, PacketType.RunningApps)
                )
            }
        }

        fun createDataChannelPacket(string: String, type: PacketType): DataChannel.Buffer {
            val combined =
                (mappings.entries.find { it.value == type.typename }?.key ?: type.typename) + string
            return DataChannel.Buffer(java.nio.ByteBuffer.wrap(combined.toByteArray()), false)
        }

        enum class PacketType(val typename: String) {
            InstalledApps("InstalledApps"),
            RunningApps("RunningApps"),
            RunApp("RunApp"),
            StopApp("StopApp"),
            Resize("Resize"),
            KeyEvent("KeyEvent"),
            MotionEvent("MotionEvent"),
        }

        fun processDataMessage(
            ctx: Context,
            connectionId: String,
            message: String,
            mediaCore: MediaCore,
            svc: IRootService
        ) {
            val type = mappings[message[0].toString()]?.let { PacketType.valueOf(it) }
            if (type == null) return
            val content = message.substring(1)
            when (type) {
                PacketType.RunApp -> {
                    //content is packageName here
                    mediaCore.echoDisplayParams[connectionId]?.let { params ->
                        svc.launchAppOnDisplay(content, params[0])
                        mediaCore.appRemoteAccessHistory[connectionId]?.let {
                            if (!it.contains(content)) {
                                mediaCore.appRemoteAccessHistory[connectionId] = it + content
                            }
                        } ?: run {
                            mediaCore.appRemoteAccessHistory[connectionId] = listOf(content)
                        }
                    }
                }

                PacketType.StopApp -> {
                    //content is packageName here
                    svc.exitTasks(content)?.let {
                        if (it.contains("error", true)) {
                            print(it)
                        } else {
                            mediaCore.appRemoteAccessHistory[connectionId]?.let { apps ->
                                mediaCore.appRemoteAccessHistory[connectionId] = apps - content
                            }
                        }
                    }
                }

                PacketType.Resize -> {
                    //content is json hashmap of width, height, scale
                    val dimensions = jsonToHashMap(content)
                    if (dimensions.isNotEmpty()) {
                        println(dimensions)
                        mediaCore.echoDisplayParams[connectionId]?.let { params ->
                            val width = dimensions["width"]?.toIntOrNull() ?: params[1]
                            val height = dimensions["height"]?.toIntOrNull() ?: params[2]
                            val scale = dimensions["scale"]?.toFloatOrNull() ?: 1f
                            val density = computedDensity(ctx, width, height, scale)
                            println(
                                "Scale : $scale, params3: ${params[3]}, Density: $density"
                            )
                            mediaCore.echoDisplayParams[connectionId] = arrayOf(
                                params[0], width, height, density
                            )
                            mediaCore.sessionCapturerResizers[connectionId]?.invoke(
                                width,
                                height,
                                density
                            )
                        }
                    }
                }

                PacketType.KeyEvent -> {
                    try {
                        val keyEventData = jsonToHashMap(content)
                        val keyEvent = MapKeyEvent.buildKeyEvent(
                            keyEventData["downTime"]?.toLongOrNull() ?: System.currentTimeMillis(),
                            keyEventData["eventTime"]?.toLongOrNull() ?: System.currentTimeMillis(),
                            keyEventData["action"]?.toIntOrNull() ?: KeyEvent.ACTION_DOWN,
                            keyEventData["keyCode"]?.toIntOrNull() ?: KeyEvent.KEYCODE_UNKNOWN,
                            keyEventData["metaState"]?.toIntOrNull() ?: 0
                        )
                        mediaCore.echoDisplayParams[connectionId]?.let { params ->
                            keyEvent?.let {
                                svc.dispatchKey(
                                    it.keyCode,
                                    keyEvent.action,
                                    params[0],
                                    keyEvent.metaState
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        print("Error processing key event: ${e.message}")
                    }
                }

                PacketType.MotionEvent -> {
                    try {
                        mediaCore.echoDisplayParams[connectionId]?.let { params ->
                            println(content)
                            val motionEvent = createMotionEventFromJson(content)
                            svc.dispatch(motionEvent, params[0])
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        print("Error processing motion event: ${e.message}")
                    }
                }

                else -> {
                    println(content)
                }
            }

        }

        fun shutDownRemoteSession(
            connectionId: String,
            mediaCore: MediaCore,
            svc: IRootService
        ) {
            mediaCore.appRemoteAccessHistory[connectionId]?.forEach { appPackage ->
                svc.exitTasks(appPackage)
            }
            mediaCore.appRemoteAccessHistory.remove(connectionId)
            mediaCore.echoDisplays.remove(connectionId)?.release()
            mediaCore.echoDisplayParams.remove(connectionId)
            mediaCore.sessionCapturerResizers.remove(connectionId)
        }

        fun jsonToHashMap(json: String): HashMap<String, String> {
            val obj = JSONObject(json)
            val map = HashMap<String, String>()
            obj.keys().forEach { key ->
                map[key] = obj.getString(key)
            }
            return map
        }

        fun computedDensity(ctx: Context, width: Int, height: Int, scale: Float?): Int {
            val factor = (width + height.toDouble())/ctx.resources.displayMetrics.let { it.widthPixels + it.heightPixels }
            return (ctx.resources.displayMetrics.densityDpi * factor * (scale ?: 1f)).roundToInt()
        }

        /**
         * Creates a MotionEvent from a JSON string using Gson.
         *
         * @param jsonString The JSON string representing the motion event data.
         * @return A valid MotionEvent object, or null if parsing or creation fails.
         */
        fun createMotionEventFromJson(jsonString: String): MotionEvent? {
            val gson = Gson()
            try {
                val eventData = gson.fromJson(jsonString, MotionEventData::class.java)

                return createMotionEventFromData(eventData)
            } catch (e: JsonSyntaxException) {
                // Log.e("MotionEventParser", "Failed to parse JSON with Gson", e)
                return null
            }
        }
        fun createMotionEventFromData(eventData: MotionEventData): MotionEvent{
            val pointerCount = eventData.pointers.size
            val pointerProperties = Array(pointerCount) { MotionEvent.PointerProperties() }
            val pointerCoords = Array(pointerCount) { MotionEvent.PointerCoords() }

            eventData.pointers.forEachIndexed { index, pointer ->
                pointerProperties[index].apply {
                    id = pointer.id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
                pointerCoords[index].apply {
                    x = pointer.x
                    y = pointer.y
                    pressure = pointer.pressure
                    size = pointer.size
                    pointer.axisValues?.forEach { (axis, value) ->
                        println("Axis values $axis $value")
                        setAxisValue(axis.toInt(), value)
                    }
                }
            }

            val finalAction = if (eventData.action == MotionEvent.ACTION_POINTER_DOWN ||
                eventData.action == MotionEvent.ACTION_POINTER_UP
            ) {
                eventData.action + (eventData.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            } else {
                eventData.action
            }

            return MotionEvent.obtain(
                eventData.downTime, eventData.eventTime, finalAction,
                pointerCount, pointerProperties, pointerCoords,
                0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
        }
    }

    data class PointerData(
        @SerializedName("id") val id: Int,
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("pressure") val pressure: Float,
        @SerializedName("size") val size: Float,
        @SerializedName("axisValues") val axisValues: Map<String, Float>? = null
    )

    // Data class for the top-level motion event object
    data class MotionEventData(
        @SerializedName("downTime") var downTime: Long,
        @SerializedName("eventTime") var eventTime: Long,
        @SerializedName("action") val action: Int,
        @SerializedName("actionIndex") val actionIndex: Int,
        @SerializedName("pointers") val pointers: List<PointerData>
    )
}