package dev.legendsayantan.extendroid.echo

/**
 * @author legendsayantan
 */
import android.content.Context
import android.view.Surface
import dev.legendsayantan.extendroid.VirtualDisplayNoContentActivity
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.lib.Logging
import dev.legendsayantan.extendroid.services.ExtendService
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
 * When `startCapture` is called, it creates a secure `VirtualDisplay` via RootService using the Surface
 * from the WebRTC-provided `SurfaceTextureHelper`.
 *
 * @param onSessionCreated A callback invoked with the newly created display ID.
 * @param onSessionReleased A callback invoked right before the capturer destroys the display.
 * @param onMediaProjectionStopped A callback to listen for `MediaProjection` events, like stop.
 * @param displayName The name for the created VirtualDisplay.
 * @param displayDpi The screen density for the created VirtualDisplay. Defaults to 400.
 */
class RemoteSessionRenderer(
    private val onSessionCreated: (displayId: Int) -> Unit,
    private val onSessionReleased: () -> Unit,
    private val displayName: String = "Echo_Screen",
    private val displayDpi: Int = 400
) : VideoCapturer, VideoSink {

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var displayId: Int = -1

    private var width: Int = 0
    private var height: Int = 0
    private var isDisposed = false

    lateinit var logging : Logging

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        applicationContext: Context,
        capturerObserver: CapturerObserver?
    ) {
        checkNotDisposed()
        logging = Logging(applicationContext)
        if (capturerObserver == null) {
            RuntimeException("CapturerObserver cannot be null").let {
                logging.e(it,"RemoteSessionRenderer")
                throw it
            }
        }
        if (surfaceTextureHelper == null) {
            RuntimeException("SurfaceTextureHelper cannot be null").let {
                logging.e(it,"RemoteSessionRenderer")
                throw it
            }
        }
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        checkNotDisposed()
        // The width and height for the capture are set here, as per the interface
        this.width = width
        this.height = height

        createVirtualDisplay()

        capturerObserver?.onCapturerStarted(true)
        surfaceTextureHelper?.startListening(this)
    }

    override fun stopCapture() {
        checkNotDisposed()
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper?.handler) {
            surfaceTextureHelper?.stopListening()
            capturerObserver?.onCapturerStopped()



            if (displayId != -1) {
                onSessionReleased()
                val savedDisplayId = displayId
                VirtualDisplayNoContentActivity.instances[savedDisplayId]?.finish()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    ExtendService.svc?.destroyVirtualDisplay(savedDisplayId)
                }, 1000)
                displayId = -1
                currentSurface?.release()
                currentSurface = null
            }
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        checkNotDisposed()
        // Update the dimensions
        this.width = width
        this.height = height

        if (displayId == -1) {
            return
        }

        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper?.handler) {
            onSessionReleased()
            val savedDisplayId = displayId
            VirtualDisplayNoContentActivity.instances[savedDisplayId]?.finish()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ExtendService.svc?.destroyVirtualDisplay(savedDisplayId)
            }, 500)
            displayId = -1
            currentSurface?.release()
            currentSurface = null

            createVirtualDisplay()
        }
    }

    fun updateDimensions(width: Int,height: Int, density:Int){
        logging.d("New density: $density","RemoteSessionRenderer")
        this.width = width
        this.height = height
        surfaceTextureHelper?.setTextureSize( width, height)

        if (displayId != -1) {
            ExtendService.svc?.resizeVirtualDisplay(displayId, width, height, density)
        }
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
    private var currentSurface: Surface? = null

    private fun createVirtualDisplay() {
        surfaceTextureHelper?.setTextureSize(width, height)
        val surface = Surface(surfaceTextureHelper?.surfaceTexture)
        currentSurface = surface

        displayId = ExtendService.svc?.createVirtualDisplay(
            displayName,
            width,
            height,
            displayDpi,
            surface
        ) ?: -1

        if (displayId != -1) {
            onSessionCreated(displayId)
        }
    }

    private fun checkNotDisposed() {
        if (isDisposed) {
            RuntimeException("This capturer is disposed").let {
                logging.e(it,"RemoteSessionRenderer")
                throw it
            }
        }
    }
}