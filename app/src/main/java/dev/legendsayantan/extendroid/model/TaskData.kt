package dev.legendsayantan.extendroid.model

import dev.legendsayantan.extendroid.lib.SerializableKeyEvent
import dev.legendsayantan.extendroid.lib.SerializableMotionEvent
import java.io.Serializable

/**
 * @author legendsayantan
 */
data class TaskData(
    val taskKey: String,
    val pkgName: String,
    val zoom: Float,                  // densityScale at training time
    val ratio: Float,                 // window aspect ratio (height/width)
    val displayWidth: Int,            // virtual display width in px (training)
    val displayHeight: Int,           // virtual display height in px (training)
    val displayDpi: Int,              // virtual display DPI (training)
    val launchDelayMs: Long = 1500L,  // configurable per task
    val stopAfter: Boolean = false,   // kill app when done?
    val touches: HashMap<Long, SerializableMotionEvent>,
    val keyEvents: HashMap<Long, SerializableKeyEvent> = hashMapOf(),
    val version: Int = 2              // bumped; stale v1 files auto-discarded
) : Serializable {
    companion object {
        // Pinned explicitly so an ordinary code change (e.g. a future field addition) doesn't
        // silently change the JVM's auto-computed UID and invalidate every already-saved task -
        // Java deserialization treats that the same as genuine corruption and the file gets
        // discarded (see TaskManager.loadTask/loadAllTasks).
        private const val serialVersionUID: Long = 1L
    }
}
