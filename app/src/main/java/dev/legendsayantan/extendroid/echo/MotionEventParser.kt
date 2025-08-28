// MotionEventParser.kt
package dev.legendsayantan.extendroid.echo

import dev.legendsayantan.extendroid.echo.RemoteSessionHandler.*
import dev.legendsayantan.extendroid.lib.InputEvent

/**
 * MotionEvent action constants (Android's integer action codes)
 */
object MotionActions {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_POINTER_DOWN = 5
    const val ACTION_POINTER_UP = 6
}

/**
 * Parser that accepts InputEvent items one-by-one and produces MotionEventData on SYN_REPORT.
 */
class MotionEventParser {

    // --- Known EV / ABS codes (numbers used by typical Linux input)
    companion object {
        // event types
        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val EV_ABS = 3

        // EV_SYN codes
        private const val SYN_REPORT = 0

        // commonly seen ABS_MT codes (numeric constants)
        private const val ABS_MT_SLOT = 47          // selects active slot (if device supports slots)
        private const val ABS_MT_TRACKING_ID = 57  // tracking id (>=0 active, -1 release)
        private const val ABS_MT_POSITION_X = 53
        private const val ABS_MT_POSITION_Y = 54
        private const val ABS_MT_PRESSURE = 58
        private const val ABS_MT_TOUCH_MAJOR = 48
        // A common BTN_TOUCH (EV_KEY) to indicate general touch state
        private const val BTN_TOUCH = 330
    }

    private data class SlotState(
        var trackingId: Int = -1,
        var x: Float = 0f,
        var y: Float = 0f,
        var pressure: Float = 1.0f,
        var size: Float = 0.05f,
        var active: Boolean = false
    )

    // Map of slot index -> slot state (supports devices that use slots)
    private val slots: MutableMap<Int, SlotState> = mutableMapOf()
    // snapshot of active slots as of last SYN_REPORT (used as "previous" state)
    private var lastActiveSlotsSnapshot: Map<Int, SlotState> = mapOf()

    private var currentSlot: Int = 0
    private var deviceReportsSlot = false

    // state to detect changes inside a SYN frame
    private val touchedSlotsThisFrameDown = mutableSetOf<Int>()
    private val touchedSlotsThisFrameUp = mutableSetOf<Int>()
    private var movedInFrame = false
    private var btnTouchState: Int? = null // optional state from BTN_TOUCH
    private var frameEventsSeen: Boolean = false

    // mapping kernel trackingId -> compact pointerId (0..N-1)
    private val trackingToPointerId = mutableMapOf<Int, Int>()
    private val freePointerIds: ArrayDeque<Int> = ArrayDeque()
    private var nextPointerId = 0

    // when a tracking id becomes -1 in a frame, hold it until SYN_REPORT so UP snapshot can still use it
    private val toFreeTrackingIdsThisFrame = mutableListOf<Int>()

    // time bookkeeping
    private var downTimeMs: Long = 0L  // set when first pointer goes down, reset after all up

    init {
        // initialize implicit slot 0
        slots[0] = SlotState()
        currentSlot = 0
    }

    private fun snapshotActiveSlots(): Map<Int, SlotState> {
        return slots.mapValues { (_, s) -> s.copy() }
    }
    private fun allocatePointerId(trackingId: Int): Int {
        return trackingToPointerId.getOrPut(trackingId) {
            val id = if (freePointerIds.isNotEmpty()) freePointerIds.removeFirst() else nextPointerId++
            trackingToPointerId[trackingId] = id
            id
        }
    }

    private fun freePointerIdForTracking(trackingId: Int) {
        val pid = trackingToPointerId.remove(trackingId) ?: return
        freePointerIds.addLast(pid)
    }


    /**
     * Feed a single InputEvent. Returns a MotionEventData when a complete frame (SYN_REPORT) produces an event,
     * otherwise null.
     */
    fun feed(ev: InputEvent): MotionEventData? {
        val eventTimeMs = ev.sec * 1000L + (ev.usec / 1000L)

        // If this is the first event in a frame (i.e. since last SYN), capture previous snapshot
        if (!frameEventsSeen && ev.type != EV_SYN) {
            lastActiveSlotsSnapshot = snapshotActiveSlots()
            frameEventsSeen = true
        }

        when (ev.type) {
            EV_ABS -> handleAbs(ev.code, ev.value)
            EV_KEY -> handleKey(ev.code, ev.value, eventTimeMs)
            EV_SYN -> {
                if (ev.code == SYN_REPORT) {
                    val out = produceMotionEventIfNeeded(eventTimeMs)
                    // clear per-frame trackers
                    touchedSlotsThisFrameDown.clear()
                    touchedSlotsThisFrameUp.clear()
                    movedInFrame = false
                    frameEventsSeen = false
                    // update "previous" snapshot to current active slots for next frame
                    lastActiveSlotsSnapshot = snapshotActiveSlots()
                    // free compact ids for any tracking ids that were released in this frame
                    toFreeTrackingIdsThisFrame.forEach { freePointerIdForTracking(it) }
                    toFreeTrackingIdsThisFrame.clear()

                    return out
                }
            }
        }
        return null
    }


    private fun handleAbs(code: Int, value: Int) {
        when (code) {
            ABS_MT_SLOT -> {
                deviceReportsSlot = true
                currentSlot = value
                slots.putIfAbsent(currentSlot, SlotState())
            }
            ABS_MT_TRACKING_ID -> {
                val slot = slots.getOrPut(currentSlot) { SlotState() }
                if (value >= 0) {
                    // new tracking id -> pointer down for this slot
                    val wasActive = slot.active
                    slot.trackingId = value
                    slot.active = true
                    // allocate compact pointer id now so it's available immediately
                    allocatePointerId(value)
                    if (!wasActive) touchedSlotsThisFrameDown.add(currentSlot)
                } else {
                    // -1 => pointer up for this slot
                    // capture the old tracking id so we can free its compact id after SYN_REPORT
                    val prevTracking = slot.trackingId
                    if (prevTracking >= 0) {
                        toFreeTrackingIdsThisFrame.add(prevTracking)
                    }
                    if (slot.active) touchedSlotsThisFrameUp.add(currentSlot)
                    slot.trackingId = -1
                    slot.active = false
                }
            }

            ABS_MT_POSITION_X -> {
                val slot = slots.getOrPut(currentSlot) { SlotState() }
                slot.x = value.toFloat()
                movedInFrame = true
            }
            ABS_MT_POSITION_Y -> {
                val slot = slots.getOrPut(currentSlot) { SlotState() }
                slot.y = value.toFloat()
                movedInFrame = true
            }
            ABS_MT_PRESSURE -> {
                val slot = slots.getOrPut(currentSlot) { SlotState() }
                slot.pressure = value.toFloat()
            }
            ABS_MT_TOUCH_MAJOR -> {
                val slot = slots.getOrPut(currentSlot) { SlotState() }
                slot.size = value.toFloat()
            }
            else -> {
                // unknown ABS code - ignore for now (extensible)
            }
        }
    }

    private fun handleKey(code: Int, value: Int, eventTimeMs: Long) {
        if (code == BTN_TOUCH) {
            // keep BTN_TOUCH state. When value==1 it indicates screen is touched (global),
            // value==0 means touch released. We don't emit immediately here; SYN_REPORT will finalize.
            btnTouchState = value
            if (value == 1 && downTimeMs == 0L) {
                // if global touch indicates initial down and we haven't set downTime
                downTimeMs = eventTimeMs
            }
            if (value == 0) {
                // If BTN_TOUCH reports release for devices that don't use tracking id - rely on that in SYN stage
            }
        }
    }

    /**
     * Called at SYN_REPORT boundary to decide whether to create a MotionEventData.
     */
    private fun produceMotionEventIfNeeded(eventTimeMs: Long): MotionEventData? {
        // Snapshot previous active pointers (before applying frame up/down semantics),
        // We'll reconstruct behavior:
        val prevActiveSlotsSnapshot = slots
            .filterValues { it.active || it.trackingId >= 0 } // active according to state before potential clearing above
            .mapValues { it.value.copy() } // defensive copy

        // Current active after ABS handling (slots map already updated)
        // Current active after applying ABS handling
        val currentActiveSlotsList = slots
            .filter { it.value.active && it.value.trackingId >= 0 }
            .toSortedMap()
            .map { Pair(it.key, it.value.copy()) } // copy defensive

// Prev active list should come from lastActiveSlotsSnapshot (captured at frame start)
        val prevActiveList = lastActiveSlotsSnapshot
            .filter { it.value.active && it.value.trackingId >= 0 }
            .toSortedMap()
            .map { Pair(it.key, it.value.copy()) }

        val prevCount = prevActiveList.size
        val currCount = currentActiveSlotsList.size


        // Determine which kind of event happened in this frame (priority: down -> up -> move -> maybe none)
        val downOccurred = touchedSlotsThisFrameDown.isNotEmpty()
        val upOccurred = touchedSlotsThisFrameUp.isNotEmpty()
        val moveOccurred = movedInFrame && (currCount > 0)

        // If no relevant change, do not produce an event
        if (!downOccurred && !upOccurred && !moveOccurred) {
            // but if BTN_TOUCH indicates full release for devices without tracking ids, create UP
            if (btnTouchState == 0 && prevCount > 0) {
                // emulate full up
                val pointersForUp = prevActiveList.map { slotPair ->
                    slotToPointer(slotPair.second, slotPair.first)
                }
                val action: Int
                val actionIndex: Int
                if (prevCount == 1) {
                    action = MotionActions.ACTION_UP
                    actionIndex = 0
                } else {
                    action = MotionActions.ACTION_POINTER_UP
                    // choose the first pointer index as the one that "went up" (best-effort)
                    actionIndex = 0
                }
                downTimeMs = 0L
                return MotionEventData(
                    downTime = downTimeMs,
                    eventTime = eventTimeMs,
                    action = action,
                    actionIndex = actionIndex,
                    pointers = pointersForUp
                )
            }
            return null
        }

        // Build MotionEvent depending on which change happened
        return when {
            downOccurred -> {
                // create list of pointers *after* the newly down ones were added (currentActiveSlotsList)
                val pointers = currentActiveSlotsList.map { slotPair -> slotToPointer(slotPair.second, slotPair.first) }

                // If no downTime yet (first pointer ever down), set it
                if (downTimeMs == 0L) downTimeMs = eventTimeMs

                val action: Int
                val actionIndex: Int
                action = if (prevCount == 0) {
                    MotionActions.ACTION_DOWN
                } else {
                    MotionActions.ACTION_POINTER_DOWN
                }
                // pick one of the newly-down slots as action index (first)
                val newSlot = touchedSlotsThisFrameDown.first()
                // find index of newSlot in current pointers list
                actionIndex = currentActiveSlotsList.indexOfFirst { it.first == newSlot }.coerceAtLeast(0)

                MotionEventData(
                    downTime = downTimeMs,
                    eventTime = eventTimeMs,
                    action = action,
                    actionIndex = actionIndex,
                    pointers = pointers
                )
            }

            upOccurred -> {
                // Build pointer list from prevActiveList (before removal)
                val pointersBeforeRemoval = prevActiveList.map { slotPair -> slotToPointer(slotPair.second, slotPair.first) }

                val action: Int
                val actionIndex: Int
                val oldDown = downTimeMs

                if (prevCount == 1) {
                    action = MotionActions.ACTION_UP
                    actionIndex = 0
                } else {
                    action = MotionActions.ACTION_POINTER_UP
                    val upSlot = touchedSlotsThisFrameUp.first()
                    actionIndex = prevActiveList.indexOfFirst { it.first == upSlot }.coerceAtLeast(0)
                }

                // reset downTime only after creating MotionEventData when no pointers remain
                if (currCount == 0) {
                    downTimeMs = 0L
                }

                MotionEventData(
                    downTime = oldDown,
                    eventTime = eventTimeMs,
                    action = action,
                    actionIndex = actionIndex,
                    pointers = pointersBeforeRemoval
                )
            }


            moveOccurred -> {
                // ACTION_MOVE with current active pointers
                val pointers = currentActiveSlotsList.map { slotPair -> slotToPointer(slotPair.second, slotPair.first) }
                MotionEventData(
                    downTime = downTimeMs,
                    eventTime = eventTimeMs,
                    action = MotionActions.ACTION_MOVE,
                    actionIndex = 0,
                    pointers = pointers
                )
            }

            else -> null
        }
    }

    private fun slotToPointer(slot: SlotState, slotIndex: Int): PointerData {
        // prefer compact pointer id mapped from kernel trackingId; if none, fallback to slotIndex
        val pid = if (slot.trackingId >= 0) allocatePointerId(slot.trackingId) else slotIndex
        return PointerData(
            id = pid,
            x = slot.x,
            y = slot.y,
            pressure = slot.pressure,
            size = slot.size,
            axisValues = null
        )
    }
}
