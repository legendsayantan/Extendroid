package dev.legendsayantan.extendroid.lib

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import dev.legendsayantan.extendroid.R;
import java.util.Locale

class Logging(val ctx: Context) {
    val logs = ctx.getSharedPreferences("logs", Context.MODE_PRIVATE)
    val notiManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    fun saveLog(level: Level, tag: String?, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())
        val logEntry = if(tag!=null) "[$tag] $message\n" else "$message\n"
        logs.edit().putString("$timestamp|${level.key}", logEntry).apply()
    }

    fun d(debug: String, tag: String?) {
        saveLog(Level.DEBUG, tag, debug)
        println(debug)
    }

    fun i(info: String, tag: String?) {
        saveLog(Level.INFO, tag, info)
        println(info)
    }

    fun e(error: String, tag: String?) {
        saveLog(Level.ERROR, tag, error)
        System.err.println(error)
    }

    fun e(error: Throwable, tag: String?) {
        saveLog(Level.ERROR, tag, "${error.message}\n${error.cause}\n${error.stackTraceToString()}")
        error.printStackTrace()
    }

    fun notify(title:String, message: String, channelId: String = "Extendroid") {
        notiManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                channelId,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        notiManager.notify(title.hashCode(), android.app.Notification.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.logo)
            .build())
        saveLog(Level.NOTIFICATION, channelId, "$title\n$message")
    }

    fun clearLogsOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
        val keysToRemove = logs.all.keys.filter { key ->
            val timestampStr = key.split("|").firstOrNull()?.removePrefix("[")?.removeSuffix("]")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(timestampStr ?: "")?.time
            timestamp != null && timestamp < cutoff
        }
        logs.edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
        i("Logs older than $days days were cleared.","Logging")
    }

    fun getLogs(): Map<String, String> {
        return logs.all.mapValues { it.value as String }
    }

    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun getLogsOf(days: Int, descendingOrder: Boolean): Map<String, String> {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000

        val filtered = logs.all.asSequence().filter { entry ->
            val key = entry.key
            val sep = key.indexOf('|')
            if (sep == -1) return@filter false

            val timestampStr = key.take(sep).removePrefix("[").removeSuffix("]")
            val timestamp = try {
                logDateFormat.parse(timestampStr)?.time
            } catch (e: Exception) {
                null
            }
            timestamp != null && timestamp >= cutoff
        }

        val result = if (descendingOrder) {
            filtered.sortedByDescending { it.key }.associate { it.key to it.value as String }
        } else {
            filtered.sortedBy { it.key }.associate { it.key to it.value as String }
        }

        return result
    }



    enum class Level(val key: String) {
        INFO("i"),
        ERROR("e"),
        DEBUG("d"),
        NOTIFICATION("n");

        companion object {
            fun fromKey(key: String?): Level? {
                return Level.entries.find { it.key==key }
            }
        }
    }

}