package dev.legendsayantan.extendroid.lib

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * @author legendsayantan
 */
class StreamHandler {
    companion object{
        var onFrameAvailable : (Int,Bitmap)->Unit = {id,bitmap->}
        val threads = hashMapOf<Int,StreamThread>()

        fun start(id:Int,surface: TextureView){
            val thread = StreamThread(id).apply { start() }
            threads[id] = thread
            Timer().schedule(timerTask {
                Handler(surface.context.mainLooper).post {
                    surface.bitmap?.let { thread.queue.add(it) }
                }
            },1000/60,1000/60)
        }

        fun start(id:Int,imageReader: ImageReader){
            val thread = StreamThread(id).apply { start() }
            threads[id] = thread
            imageReader.setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.let {
                    thread.queue.add(Utils.toBitmap(it))
                    it.close()
                }
            },null)
        }


        fun stop(id:Int){
            threads[id]?.interrupt()
            threads.remove(id)
        }


        class StreamThread(val id: Int) : HandlerThread(id.toString()) {
            val queue = arrayListOf<Bitmap>()
            override fun run() {
                while (true) {
                    if (queue.isNotEmpty()) {
                        onFrameAvailable(id,queue.removeAt(0))
                    }
                }
            }
        }
    }
}