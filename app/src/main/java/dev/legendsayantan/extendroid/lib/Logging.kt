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
        maybePrune()
    }

    // SharedPreferences backs this with a single in-memory map serialized to one XML file on
    // every write - it was never bounded, so a long/verbose echo session could grow it
    // indefinitely. Only check the size every PRUNE_CHECK_INTERVAL writes to keep the overhead
    // of this safety net low; an occasional missed or extra check is harmless.
    private fun maybePrune() {
        if (writeCounter.incrementAndGet() % PRUNE_CHECK_INTERVAL != 0) return
        val all = logs.all
        if (all.size <= MAX_LOG_ENTRIES) return
        // Keys are "yyyy-MM-dd HH:mm:ss.SSS|level" - fixed-width and zero-padded, so lexical
        // sort order matches chronological order (same assumption getLogsOf/clearLogsOlderThan
        // already rely on).
        val oldestFirst = all.keys.sorted()
        val toRemove = oldestFirst.size - MAX_LOG_ENTRIES
        if (toRemove <= 0) return
        logs.edit().apply {
            oldestFirst.take(toRemove).forEach { remove(it) }
        }.apply()
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
            val timestamp = logDateFormat.parse(timestampStr ?: "")?.time
            timestamp != null && timestamp < cutoff
        }
        logs.edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
        i("Logs older than ${logDateFormat.format(cutoff)} were cleared.","Logging")
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

    companion object {
        private const val MAX_LOG_ENTRIES = 2000
        private const val PRUNE_CHECK_INTERVAL = 50
        private val writeCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }
}