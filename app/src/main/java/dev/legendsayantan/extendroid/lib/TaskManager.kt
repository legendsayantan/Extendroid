package dev.legendsayantan.extendroid.lib

import android.content.Context
import dev.legendsayantan.extendroid.model.TaskData
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream

/**
 * @author legendsayantan
 */
object TaskManager {
    fun taskExists(ctx: Context, name: String): Boolean {
        val dir = File(ctx.filesDir, "tasks")
        if (!dir.exists()) return false
        return dir.listFiles()?.any { it.name.endsWith("_$name.dat") } ?: false
    }

    fun saveTask(ctx: Context, task: TaskData) {
        try {
            val dir = File(ctx.filesDir, "tasks")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${task.pkgName}_${task.taskKey}.dat")
            ObjectOutputStream(FileOutputStream(file)).use {
                it.writeObject(task)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
