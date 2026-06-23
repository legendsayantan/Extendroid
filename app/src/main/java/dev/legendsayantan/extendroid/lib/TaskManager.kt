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

    fun updateTask(ctx: Context, task: TaskData) {
        saveTask(ctx, task) // overwrite is same as save
    }

    fun loadTask(ctx: Context, pkgName: String, taskKey: String): TaskData? {
        val file = File(File(ctx.filesDir, "tasks"), "${pkgName}_${taskKey}.dat")
        if (!file.exists()) return null
        return try {
            java.io.ObjectInputStream(java.io.FileInputStream(file)).use {
                it.readObject() as TaskData
            }
        } catch (e: Exception) {
            e.printStackTrace()
            file.delete() // Silently discard invalid/stale tasks
            null
        }
    }

    fun loadAllTasks(ctx: Context): List<TaskData> {
        val dir = File(ctx.filesDir, "tasks")
        if (!dir.exists()) return emptyList()
        val validTasks = mutableListOf<TaskData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".dat")) {
                try {
                    java.io.ObjectInputStream(java.io.FileInputStream(file)).use {
                        validTasks.add(it.readObject() as TaskData)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    file.delete() // Silently discard invalid/stale tasks
                }
            }
        }
        // sort by pkgName then taskKey
        return validTasks.sortedWith(compareBy({ it.pkgName }, { it.taskKey }))
    }

    fun deleteTask(ctx: Context, pkgName: String, taskKey: String) {
        val file = File(File(ctx.filesDir, "tasks"), "${pkgName}_${taskKey}.dat")
        if (file.exists()) {
            file.delete()
        }
    }

    fun getTasksForPackage(ctx: Context, pkgName: String): List<TaskData> {
        return loadAllTasks(ctx).filter { it.pkgName == pkgName }
    }
}
