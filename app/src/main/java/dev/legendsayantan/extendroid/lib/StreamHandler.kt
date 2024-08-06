package dev.legendsayantan.extendroid.lib

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.HandlerThread
import android.view.TextureView
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
class StreamHandler {
    companion object {
        private const val MAX_FRAME_RATE = 15
        var onFrameAvailable: (Int, Int, Bitmap?) -> Unit = { id, quality, bitmap -> }
        private val allThreads = hashMapOf<Int, StreamThread>()

        init {
            //periodic optimiser
            Timer().schedule(timerTask {
                allThreads.forEach { (t, u) ->
                    if (u.queue.size>10) u.queue = arrayListOf()
                }
            },5000,5000)
        }

        fun start(
            id: Int,
            textureView: TextureView? = null,
            imageReader: ImageReader? = null,
            udpServer: UdpServer
        ): Int {
            val thread = StreamThread(
                id,
                calculateOptimalQuality(textureView, imageReader)
            ).apply { start() }
            var lastFrameTime = 0L
            val frameDuration = 1000 / MAX_FRAME_RATE
            var lastFrame : Bitmap? = null
            if (textureView != null) {
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        val now = System.currentTimeMillis()
                        val frame = textureView.bitmap
                        if (now - lastFrameTime > frameDuration && frame?.sameAs(lastFrame) == false) {
                            thread.queue.add(frame)
                            lastFrameTime = now
                            lastFrame = frame
                        }
                    }
                }
            } else if (imageReader != null) {
                val sendFrame: (ImageReader) -> Unit = { reader ->
                    reader.acquireLatestImage()?.let { image ->
                        Utils.toBitmap(image).let { bitmap ->
                            thread.queue.add(bitmap)
                        }
                        image.close()
                    }
                    lastFrameTime = System.currentTimeMillis()
                }
                imageReader.setOnImageAvailableListener({ reader ->
                    sendFrame(reader)
                }, null)
                Timer().schedule(timerTask {
                    if ((System.currentTimeMillis() - lastFrameTime) > 10000) {
                        sendFrame(imageReader)
                    }
                }, 2000, 2000)
            } else return -1
            allThreads[id] = thread
            return udpServer.create(id)
        }


        fun stop(id: Int, udpServer: UdpServer) {
            allThreads[id]?.interrupt()
            allThreads.remove(id)
            udpServer.close(id)
        }

        private fun calculateOptimalQuality(
            textureView: TextureView?,
            imageReader: ImageReader?
        ): Int {
            val pixelCount = ((textureView?.let {
                it.height * it.width
            } ?: imageReader?.let {
                it.height * it.width
            } ?: (720 * 1080)))
            val x1 = 480*480
            val y1 = 30
            val x2 = 2160*3840
            val y2 = 0
            val m = (y2 - y1).toDouble() / (x2 - x1).toDouble()
            val b = y1 - m * x1
            return (m * pixelCount + b).toInt()
        }


        class StreamThread(val id: Int, val compression: Int) : HandlerThread(id.toString()) {
            var queue = arrayListOf<Bitmap>()
            override fun run() {
                while (true) {
                    if (queue.isNotEmpty()) {
                        onFrameAvailable(id, compression, queue.removeAt(0))
                    }
                }
            }
        }
    }
}