package dev.legendsayantan.extendroid.model

/**
 * @author legendsayantan
 */

/**
 * Item data class for StaggeredGridAdapter
 * @param name The text to display in the item
 * @param ratio The width/height ratio for the item
 * */
data class WindowData(
    val name: String,
    val packageName: String,
    val ratio: Float
)