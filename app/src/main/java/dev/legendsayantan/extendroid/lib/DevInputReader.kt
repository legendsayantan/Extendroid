package dev.legendsayantan.extendroid.lib

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * @author legendsayantan
 */
data class InputEvent(
    val devicePath: String,
    val sec: Long,
    val usec: Long,
    val type: Int,
    val code: Int,
    val value: Int
)

interface InputEventListener {
    fun onEvent(event: InputEvent)
    fun onDeviceAdded(devicePath: String) {}
    fun onDeviceRemoved(devicePath: String) {}
    fun onError(devicePath: String?, throwable: Throwable) {}
}

class DevInputReader(
    private val inputDir: String = "/dev/input",
    private val pollIntervalMs: Long = 5000L, // poll for new/removed devices
    private val listener: InputEventListener
) {

    private val readers = ConcurrentHashMap<String, DeviceReader>()
    private val executor = Executors.newCachedThreadPool { r ->
        thread(start = false, block = { r.run() }) // create daemon-like threads
    }
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var pollTask: ScheduledFuture<*>? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        // initial scan + schedule periodic scan
        scanAndSyncDevices()
        pollTask = scheduler.scheduleWithFixedDelay(
            { scanAndSyncDevices() }, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        running = false
        pollTask?.cancel(true)
        scheduler.shutdownNow()
        // stop and close all device readers
        val snapshot = readers.values.toList()
        snapshot.forEach { it.stop() }
        readers.clear()
        executor.shutdownNow()
    }

    private fun scanAndSyncDevices() {
        try {
            val dir = File(inputDir)
            if (!dir.exists() || !dir.isDirectory) return
            var deviceFiles = dir.listFiles { f -> f.name.startsWith("event") }?.map { it.absolutePath } ?: emptyList()

            // Add new devices
            for (path in deviceFiles) {
                if (!readers.containsKey(path)) {
                    val r = DeviceReader(path, listener)
                    readers[path] = r
                    listener.onDeviceAdded(path)
                    // start reading on a background thread via executor
                    executor.submit { r.runReadingLoop() }
                }
            }

            // Remove devices that disappeared
            val known = readers.keys.toList()
            for (knownPath in known) {
                if (!deviceFiles.contains(knownPath)) {
                    val removed = readers.remove(knownPath)
                    removed?.stop()
                    listener.onDeviceRemoved(knownPath)
                }
            }
        } catch (t: Throwable) {
            listener.onError(null, t)
        }
    }

    private class DeviceReader(
        private val devicePath: String,
        private val listener: InputEventListener
    ) {
        @Volatile private var stopped = false
        private var fis: FileInputStream? = null
        private var bis: BufferedInputStream? = null

        fun stop() {
            stopped = true
            try {
                bis?.close()
            } catch (_: Exception) {}
            try {
                fis?.close()
            } catch (_: Exception) {}
        }

        fun runReadingLoop() {
            try {
                fis = FileInputStream(devicePath)
                bis = BufferedInputStream(fis)
                val buf = ByteArray(24)
                while (!stopped && Thread.currentThread().isInterrupted.not()) {
                    // read exactly 24 bytes
                    val ok = readExactly(bis!!, buf)
                    if (!ok) {
                        // EOF or device removed
                        break
                    }
                    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                    val sec = bb.long
                    val usec = bb.long
                    val type = bb.short.toInt() and 0xFFFF
                    val code = bb.short.toInt() and 0xFFFF
                    val value = bb.int

                    val event = InputEvent(devicePath, sec, usec, type, code, value)
                    try {
                        listener.onEvent(event)
                    } catch (t: Throwable) {
                        // listener shouldn't crash our reader loop
                        listener.onError(devicePath, t)
                    }
                }
            } catch (io: IOException) {
                listener.onError(devicePath, io)
            } catch (t: Throwable) {
                listener.onError(devicePath, t)
            } finally {
                stop()
            }
        }

        // read exactly buf.size bytes; return false on EOF or failure
        private fun readExactly(bis: BufferedInputStream, buf: ByteArray): Boolean {
            var offset = 0
            val wanted = buf.size
            while (offset < wanted && !stopped) {
                val r = try {
                    bis.read(buf, offset, wanted - offset)
                } catch (io: IOException) {
                    return false
                }
                if (r == -1) return false
                offset += r
            }
            return offset == wanted
        }
    }
}
