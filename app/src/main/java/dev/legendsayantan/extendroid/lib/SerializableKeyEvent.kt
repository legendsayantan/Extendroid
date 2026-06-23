package dev.legendsayantan.extendroid.lib

import java.io.Serializable

/**
 * A lightweight serializable wrapper for Android key events.
 * Stores the Android keycode directly (converted from Linux scan codes at capture time).
 *
 * @author legendsayantan
 */
data class SerializableKeyEvent(
    val action: Int,     // KeyEvent.ACTION_DOWN = 0 / ACTION_UP = 1
    val keyCode: Int,    // Android KEYCODE_*
    val metaState: Int
) : Serializable
