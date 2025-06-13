package dev.legendsayantan.extendroid.model

import android.graphics.drawable.Drawable

/**
 * @author legendsayantan
 */

data class AppItem(
    val image: Drawable,
    val appName: String,
    val packageName: String
)
