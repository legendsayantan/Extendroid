package dev.legendsayantan.extendroid.lib

import android.os.SystemClock
import android.view.MotionEvent
import java.io.Serializable

/**
 * A serializable representation of an Android MotionEvent.
 * This class captures the essential data from a MotionEvent to allow
 * for serialization (e.g., across processes, saving to disk) and
 * later reconstruction.
 *
 * @author legendsayantan
 *
 */

data class SerializableMotionEvent(
    val downTime: Long,
    val eventTime: Long,
    val action: Int,
    val pointerCount: Int,
    val pointerProperties: Array<SerializablePointerProperties>,
    val pointerCoords: Array<SerializablePointerCoords>,
    val metaState: Int,
    val buttonState: Int,
    val xPrecision: Float,
    val yPrecision: Float,
    val deviceId: Int,
    val edgeFlags: Int,
    val source: Int,
    val flags: Int
) : Serializable {

    data class SerializablePointerProperties(
        val id: Int,
        val toolType: Int
    ) : Serializable

    data class SerializablePointerCoords(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val size: Float,
        val touchMajor: Float,
        val touchMinor: Float,
        val toolMajor: Float,
        val toolMinor: Float,
        val orientation: Float
    ) : Serializable

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializableMotionEvent

        if (downTime != other.downTime) return false
        if (eventTime != other.eventTime) return false
        if (action != other.action) return false
        if (pointerCount != other.pointerCount) return false
        if (!pointerProperties.contentEquals(other.pointerProperties)) return false
        if (!pointerCoords.contentEquals(other.pointerCoords)) return false
        if (metaState != other.metaState) return false
        if (buttonState != other.buttonState) return false
        if (xPrecision != other.xPrecision) return false
        if (yPrecision != other.yPrecision) return false
        if (deviceId != other.deviceId) return false
        if (edgeFlags != other.edgeFlags) return false
        if (source != other.source) return false
        if (flags != other.flags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = downTime.hashCode()
        result = 31 * result + eventTime.hashCode()
        result = 31 * result + action
        result = 31 * result + pointerCount
        result = 31 * result + pointerProperties.contentHashCode()
        result = 31 * result + pointerCoords.contentHashCode()
        result = 31 * result + metaState
        result = 31 * result + buttonState
        result = 31 * result + xPrecision.hashCode()
        result = 31 * result + yPrecision.hashCode()
        result = 31 * result + deviceId
        result = 31 * result + edgeFlags
        result = 31 * result + source
        result = 31 * result + flags
        return result
    }
}

/**
 * Extension function to convert a MotionEvent to a SerializableMotionEvent.
 *
 * @return A SerializableMotionEvent containing the data from the original event.
 */
fun MotionEvent.toSerializable(): SerializableMotionEvent {
    val pointerCount = this.pointerCount

    val props = Array(pointerCount) { SerializableMotionEvent.SerializablePointerProperties(0, 0) }
    val coords = Array(pointerCount) {
        SerializableMotionEvent.SerializablePointerCoords(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    for (i in 0 until pointerCount) {
        val pointerProperties = MotionEvent.PointerProperties()
        this.getPointerProperties(i, pointerProperties)
        props[i] = SerializableMotionEvent.SerializablePointerProperties(
            id = pointerProperties.id,
            toolType = pointerProperties.toolType
        )

        val pointerCoords = MotionEvent.PointerCoords()
        this.getPointerCoords(i, pointerCoords)
        coords[i] = SerializableMotionEvent.SerializablePointerCoords(
            x = pointerCoords.x,
            y = pointerCoords.y,
            pressure = pointerCoords.pressure,
            size = pointerCoords.size,
            touchMajor = pointerCoords.touchMajor,
            touchMinor = pointerCoords.touchMinor,
            toolMajor = pointerCoords.toolMajor,
            toolMinor = pointerCoords.toolMinor,
            orientation = pointerCoords.orientation
        )
    }

    return SerializableMotionEvent(
        downTime = this.downTime,
        eventTime = this.eventTime,
        action = this.action,
        pointerCount = pointerCount,
        pointerProperties = props,
        pointerCoords = coords,
        metaState = this.metaState,
        buttonState = this.buttonState,
        xPrecision = this.xPrecision,
        yPrecision = this.yPrecision,
        deviceId = this.deviceId,
        edgeFlags = this.edgeFlags,
        source = this.source,
        flags = this.flags
    )
}

/**
 * Extension function to convert a SerializableMotionEvent back to an Android MotionEvent.
 * Note: Historical data (batching) is not preserved through this serialization process.
 *
 * @return A new MotionEvent reconstructed from the serialized data.
 */
fun SerializableMotionEvent.toMotionEvent(): MotionEvent {
    val pointerProperties = Array(this.pointerCount) { MotionEvent.PointerProperties() }
    val pointerCoords = Array(this.pointerCount) { MotionEvent.PointerCoords() }

    for (i in 0 until this.pointerCount) {
        val sProps = this.pointerProperties[i]
        pointerProperties[i].id = sProps.id
        pointerProperties[i].toolType = sProps.toolType

        val sCoords = this.pointerCoords[i]
        pointerCoords[i].x = sCoords.x
        pointerCoords[i].y = sCoords.y
        pointerCoords[i].pressure = sCoords.pressure
        pointerCoords[i].size = sCoords.size
        pointerCoords[i].touchMajor = sCoords.touchMajor
        pointerCoords[i].touchMinor = sCoords.touchMinor
        pointerCoords[i].toolMajor = sCoords.toolMajor
        pointerCoords[i].toolMinor = sCoords.toolMinor
        pointerCoords[i].orientation = sCoords.orientation
    }

    return MotionEvent.obtain(
        this.downTime,
        this.eventTime,
        this.action,
        this.pointerCount,
        pointerProperties,
        pointerCoords,
        this.metaState,
        this.buttonState,
        this.xPrecision,
        this.yPrecision,
        this.deviceId,
        this.edgeFlags,
        this.source,
        this.flags
    )
}