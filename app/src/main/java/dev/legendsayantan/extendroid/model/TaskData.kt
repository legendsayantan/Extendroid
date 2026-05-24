package dev.legendsayantan.extendroid.model

import dev.legendsayantan.extendroid.lib.SerializableMotionEvent
import java.io.Serializable

/**
 * @author legendsayantan
 */
data class TaskData(
    val taskKey: String,
    val pkgName: String,
    val zoom: Float,
    val ratio: Float,
    val touches: HashMap<Long, SerializableMotionEvent>
) : Serializable
