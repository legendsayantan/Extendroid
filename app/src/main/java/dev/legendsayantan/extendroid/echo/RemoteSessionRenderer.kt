package dev.legendsayantan.extendroid.echo

/**
 * @author legendsayantan
 */
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * A custom VideoCapturer for screen sharing that allows the application to manage the
 * lifecycle of the VirtualDisplay.
 *
 * This capturer accepts a pre-authorized `MediaProjection` instance. When `startCapture` is called,
 * it creates a `VirtualDisplay` using the Surface from the WebRTC-provided `SurfaceTextureHelper`.
 * It then notifies the application via a callback, handing over the `VirtualDisplay` instance.
 * The application is then responsible for releasing this `VirtualDisplay` when it's done.
 *
 * @param mediaProjection An active `MediaProjection` instance obtained from the Android framework.
 * @param mediaProjectionCallback A callback to listen for `MediaProjection` events, like stop.
 * @param onSessionCreated A callback invoked with the newly created `VirtualDisplay`. The app
 * should store this and release it when capture is finished.
 * @param onSessionReleased A callback invoked right before the capturer stops using the
 * `VirtualDisplay`, signaling that it's safe to release.
 * @param displayName The name for the created VirtualDisplay.
 * @param displayDpi The screen density for the created VirtualDisplay. Defaults to 400.
 */
class RemoteSessionRenderer(
    private val mediaProjection: MediaProjection,
    private val mediaProjectionCallback: MediaProjection.Callback,
    private val onSessionCreated: (virtualDisplay: VirtualDisplay) -> Unit,
    private val onSessionReleased: () -> Unit,
    private val displayName: String = "Echo_Screen",
    private val displayDpi: Int = 400
) : VideoCapturer, VideoSink {

    private companion object {
        // Flags remain constant
        val DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
    }

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var width: Int = 0
    private var height: Int = 0
    private var isDisposed = false

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        applicationContext: Context?,
        capturerObserver: CapturerObserver?
    ) {
        checkNotDisposed()
        if (capturerObserver == null) {
            throw RuntimeException("CapturerObserver cannot be null")
        }
        if (surfaceTextureHelper == null) {
            throw RuntimeException("SurfaceTextureHelper cannot be null")
        }
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        checkNotDisposed()
        // The width and height for the capture are set here, as per the interface
        this.width = width
        this.height = height

        mediaProjection.registerCallback(mediaProjectionCallback, surfaceTextureHelper?.handler)

        createVirtualDisplay()

        capturerObserver?.onCapturerStarted(true)
        surfaceTextureHelper?.startListening(this)
    }

    override fun stopCapture() {
        checkNotDisposed()
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper?.handler) {
            surfaceTextureHelper?.stopListening()
            capturerObserver?.onCapturerStopped()

            mediaProjection.unregisterCallback(mediaProjectionCallback)

            if (virtualDisplay != null) {
                onSessionReleased()
                virtualDisplay = null
            }
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        checkNotDisposed()
        // Update the dimensions
        this.width = width
        this.height = height

        if (virtualDisplay == null) {
            return
        }

        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper?.handler) {
            onSessionReleased()
            virtualDisplay?.release()

            createVirtualDisplay()
        }
    }

    fun updateDimensions(width: Int,height: Int, density:Int){
        println("New density: $density")
        this.width = width
        this.height = height
        surfaceTextureHelper?.setTextureSize( width, height)
        val surface = Surface(surfaceTextureHelper?.surfaceTexture)
        virtualDisplay?.surface = surface
        virtualDisplay?.resize(width, height, density)
    }

    override fun dispose() {
        isDisposed = true
    }

    override fun isScreencast(): Boolean {
        return true
    }

    override fun onFrame(frame: VideoFrame) {
        capturerObserver?.onFrameCaptured(frame)
    }

    private fun createVirtualDisplay() {
        surfaceTextureHelper?.setTextureSize(width, height)
        val surface = Surface(surfaceTextureHelper?.surfaceTexture)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            displayName,
            width,
            height,
            displayDpi,
            DISPLAY_FLAGS,
            surface,
            null,
            null
        )?.also {
            onSessionCreated(it)
        }
    }

    private fun checkNotDisposed() {
        if (isDisposed) {
            throw RuntimeException("This capturer is disposed")
        }
    }
}