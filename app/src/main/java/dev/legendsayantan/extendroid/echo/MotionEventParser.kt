package dev.legendsayantan.extendroid.echo

import dev.legendsayantan.extendroid.echo.RemoteSessionHandler.*;
import dev.legendsayantan.extendroid.lib.InputEvent

// Parser that accepts InputEvent (as in your listener) and emits MotionEventData on SYN_REPORT
class MotionEventParser {

    // Linux/evdev constants (common values)
    companion object {
        const val EV_SYN = 0x00
        const val EV_KEY = 0x01
        const val EV_ABS = 0x03

        const val SYN_REPORT = 0x00
        const val SYN_DROPPED = 0x03

        // ABS codes (common)
        const val ABS_X = 0      // 0
        const val ABS_Y = 1      // 1
        const val ABS_PRESSURE = 24 // 24

        // in companion object
        const val ABS_MT_PRESSURE = 58  // multitouch pressure (ABS_MT_PRESSURE)

        // Multi-touch (ABS_MT_*) codes (common values)
        const val ABS_MT_SLOT = 47          // select slot
        const val ABS_MT_TOUCH_MAJOR = 48   // touch major
        const val ABS_MT_TOUCH_MINOR = 49   // touch minor
        const val ABS_MT_POSITION_X = 53    // mt position x
        const val ABS_MT_POSITION_Y = 54    // mt position y
        const val ABS_MT_TRACKING_ID = 57   // tracking id
        // (If your device uses other ABS codes, adapt accordingly)

        // EV_KEY codes
        const val BTN_TOUCH = 330
    }

    // Android MotionEvent action ints (stable)
    private object Actions {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_OUTSIDE = 4
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }

    // Internal slot state for type-B multitouch
    private data class SlotState(
        var trackingId: Int = -1,
        var x: Int = 0,
        var y: Int = 0,
        var pressure: Int = 0,
        var touchMajor: Int = 0,
        var touchMinor: Int = 0,
        val axisValues: MutableMap<String, Float> = mutableMapOf()
    )

    // parser state
    private var currentSlot = 0
    private val slots = mutableMapOf<Int, SlotState>() // slotIndex -> SlotState
    private var singleTouchX = 0
    private var singleTouchY = 0
    private var singleTouchPressure = 0
    private var btnTouchDown = false

    // for downTime semantics
    private var gestureDownTimeMs: Long = 0L
    private var prevActiveSlotsOrder: List<Int> = emptyList() // keeps ordering (slot indices) used for actionIndex mapping

    /**
     * Feed an input event. Returns a MotionEventData object when a SYN_REPORT frame completes,
     * otherwise returns null.
     *
     * Uses the `event` fields you already have: event.type (Int), event.code (Int), event.value (Int),
     * and event.sec / event.usec for timestamp.
     */
    fun feedEvent(event: InputEvent): MotionEventData? {
        // compute ms timestamp for this event
        val eventTimeMs = event.sec * 1000L + (event.usec / 1000L)

        when (event.type) {
            EV_ABS -> handleAbs(event.code, event.value)
            EV_KEY -> handleKey(event.code, event.value)
            EV_SYN -> {
                when (event.code) {
                    SYN_REPORT -> {
                        // Build a MotionEventData for the current frame (if meaningful)
                        val motion = buildFrame(eventTimeMs)
                        // after building, clear per-frame "updated" flags if any (we store persistent slot state)
                        return motion
                    }
                    SYN_DROPPED -> {
                        // Kernel dropped events and state is out-of-sync. Reset state.
                        slots.clear()
                        prevActiveSlotsOrder = emptyList()
                        gestureDownTimeMs = 0L
                    }
                    else -> {
                        // ignore other SYN codes
                    }
                }
            }
            else -> {
                // ignore other event types
            }
        }
        return null
    }

    private fun mapAbsToAxisName(code: Int): String? {
        return when (code) {
            ABS_X, ABS_MT_POSITION_X -> "AXIS_X"
            ABS_Y, ABS_MT_POSITION_Y -> "AXIS_Y"
            ABS_PRESSURE, ABS_MT_PRESSURE -> "AXIS_PRESSURE"
            ABS_MT_TOUCH_MAJOR -> "AXIS_TOUCH_MAJOR"
            ABS_MT_TOUCH_MINOR -> "AXIS_TOUCH_MINOR"
            // add more mappings here if you discover more codes you care about
            else -> null
        }
    }

    private fun handleAbs(code: Int, value: Int) {
        when (code) {
            ABS_MT_SLOT -> {
                currentSlot = value
                // ensure slot exists
                slots.getOrPut(currentSlot) { SlotState() }
            }
            ABS_MT_TRACKING_ID -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.trackingId = value // -1 usually means release
                if (value == -1) {
                    // pointer released in this slot; keep slot state but mark trackingId = -1
                }
            }
            ABS_MT_POSITION_X -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.x = value
            }
            ABS_MT_POSITION_Y -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.y = value
            }
            ABS_MT_TOUCH_MAJOR -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.touchMajor = value
            }
            ABS_MT_TOUCH_MINOR -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.touchMinor = value
            }
            ABS_MT_PRESSURE -> {
                val s = slots.getOrPut(currentSlot) { SlotState() }
                s.pressure = value
            }
            ABS_X -> {
                singleTouchX = value
            }
            ABS_Y -> {
                singleTouchY = value
            }
            ABS_PRESSURE -> {
                singleTouchPressure = value
            }
            else -> {
                // try to map known codes to Android axis names, otherwise keep raw ABS_<code>
                val s = slots.getOrPut(currentSlot) { SlotState() }
                val axisName = mapAbsToAxisName(code)
                if (axisName != null) {
                    s.axisValues[axisName] = value.toFloat()
                } else {
                    s.axisValues[code.toString()] = value.toFloat()
                }
            }
        }
    }


    private fun handleKey(code: Int, value: Int) {
        if (code == BTN_TOUCH) {
            btnTouchDown = value != 0
            // If starting a new gesture on BTN_TOUCH press, set downTime when appropriate on next SYN_REPORT
        }
    }

    private fun buildFrame(eventTimeMs: Long): MotionEventData? {
        // Determine active pointers (type B multitouch prefers slots with trackingId != -1).
        val activeSlots = slots.filterValues { it.trackingId != -1 }.toSortedMap() // sort by slot index
        val pointers = mutableListOf<PointerData>()

        if (activeSlots.isNotEmpty()) {
            // type-B multitouch
            for ((slotIndex, slotState) in activeSlots) {
                val id = if (slotState.trackingId >= 0) slotState.trackingId else slotIndex
                val pressureF = slotState.pressure.toFloat().let { if (it == 0f) slotState.touchMajor.toFloat() / 1000f else it } // heuristic
                val sizeF = (slotState.touchMajor + slotState.touchMinor).toFloat() / 2f
                val axisMap = if (slotState.axisValues.isNotEmpty()) slotState.axisValues.toMap() else null
                pointers.add(
                    PointerData(
                        id = id,
                        x = slotState.x.toFloat(),
                        y = slotState.y.toFloat(),
                        pressure = pressureF,
                        size = sizeF,
                        axisValues = axisMap
                    )
                )
            }
        } else {
            // fall back to single-touch (ABS_X/ABS_Y / BTN_TOUCH)
            if (!btnTouchDown && singleTouchPressure == 0) {
                // no touch currently -> nothing to emit
                // but we may want to emit events with empty pointer list for UP; below logic handles UP when prevActiveSlotsOrder non-empty
            }
            // If BTN_TOUCH indicates contact OR coordinates nonzero, produce one pointer
            if (btnTouchDown || singleTouchPressure > 0 || singleTouchX != 0 || singleTouchY != 0) {
                pointers.add(
                    PointerData(
                        id = 0,
                        x = singleTouchX.toFloat(),
                        y = singleTouchY.toFloat(),
                        pressure = singleTouchPressure.toFloat(),
                        size = 0f,
                        axisValues = null
                    )
                )
            }
        }

        // Decide action / actionIndex by comparing previous frame's pointers to current
        val currentSlotOrder: List<Int> = if (activeSlots.isNotEmpty()) activeSlots.keys.toList() else if (pointers.isNotEmpty()) listOf(0) else emptyList()

        val prevSet = prevActiveSlotsOrder.toSet()
        val currSet = currentSlotOrder.toSet()

        // newly added slots = curr - prev
        val added = currSet - prevSet
        // removed = prev - curr
        val removed = prevSet - currSet

        val action: Int
        var actionIndex = 0

        if (added.isNotEmpty()) {
            // choose single added pointer (if multiple added, pick first)
            val addedSlot = added.first()
            // if previously there were none -> ACTION_DOWN, else -> ACTION_POINTER_DOWN
            action = if (prevSet.isEmpty()) {
                actionIndex = currentSlotOrder.indexOf(addedSlot).coerceAtLeast(0)
                Actions.ACTION_DOWN
            } else {
                actionIndex = currentSlotOrder.indexOf(addedSlot).coerceAtLeast(0)
                Actions.ACTION_POINTER_DOWN
            }
            // When gesture starts, set downTime if not already set
            if (gestureDownTimeMs == 0L) gestureDownTimeMs = eventTimeMs
        } else if (removed.isNotEmpty()) {
            // pointer(s) removed
            val removedSlot = removed.first()
            // if result has zero pointers -> ACTION_UP, else ACTION_POINTER_UP
            action = if (currSet.isEmpty()) {
                // for UP, actionIndex is index of removed pointer in the previous ordering (we kept prevActiveSlotsOrder)
                actionIndex = prevActiveSlotsOrder.indexOf(removedSlot).coerceAtLeast(0)
                Actions.ACTION_UP
            } else {
                // when pointer up but others still present
                actionIndex = prevActiveSlotsOrder.indexOf(removedSlot).coerceAtLeast(0)
                Actions.ACTION_POINTER_UP
            }
            if (currSet.isEmpty()) {
                // gesture ended -> reset downTime
                gestureDownTimeMs = 0L
            }
        } else if (pointers.isNotEmpty()) {
            // no add/remove -> movement or stationary press
            action = Actions.ACTION_MOVE
            actionIndex = 0
            if (gestureDownTimeMs == 0L) gestureDownTimeMs = eventTimeMs // safety
        } else {
            // nothing meaningful (no pointers now and none before)
            // update prev and return null
            prevActiveSlotsOrder = currentSlotOrder
            return null
        }

        val downTimeFinal = if (gestureDownTimeMs != 0L) gestureDownTimeMs else eventTimeMs

        // Build MotionEventData
        val motion = MotionEventData(
            downTime = downTimeFinal,
            eventTime = eventTimeMs,
            action = action,
            actionIndex = actionIndex,
            pointers = pointers
        )

        // Save current ordering for next frame comparison
        prevActiveSlotsOrder = currentSlotOrder

        return motion
    }
}