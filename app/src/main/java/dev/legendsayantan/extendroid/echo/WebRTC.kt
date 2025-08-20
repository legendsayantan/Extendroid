package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*
import java.sql.Time
import java.util.Timer
import kotlin.concurrent.timerTask

class WebRTC {
    companion object {

        private val peerConnections = hashMapOf<Long, PeerConnection>()
        private lateinit var peerConnectionFactory: PeerConnectionFactory

        fun checkAndStart(
            ctx: Context,
            uid: String,
            token: String,
            data: Map<String, String>,
            localVideoTrack: VideoTrack,
            onStateChanged: (PeerConnection.IceConnectionState) -> Unit,
            onDataMessage: (String) -> Unit
        ){
            if (data["fetchsdp"] == "true") {
                //if so, fetch the sdp from the backend
                EchoNetworkUtils.getSignalWithCallback(ctx, uid, token) { str, ex ->
                    if (str != null && ex == null) {
                        try {
                            println(str)
                            val obj = org.json.JSONObject(str)

                            // --- TURN / STUN servers ---
                            val turnJson = obj.getString("turncreds")
                            val turnObj = org.json.JSONObject(turnJson)
                            val serversArr = turnObj.getJSONArray("iceServers")
                            val iceServers = mutableListOf<PeerConnection.IceServer>()
                            for (i in 0 until serversArr.length()) {
                                val s = serversArr.getJSONObject(i)
                                val urls = s.getJSONArray("urls")
                                val urlList = mutableListOf<String>()
                                for (j in 0 until urls.length()) urlList.add(urls.getString(j))
                                val username = s.optString("username", null)
                                val credential = s.optString("credential", null)
                                val iceServer = if (username != null && credential != null) {
                                    PeerConnection.IceServer.builder(urlList).setUsername(username).setPassword(credential).createIceServer()
                                } else {
                                    PeerConnection.IceServer.builder(urlList).createIceServer()
                                }
                                iceServers.add(iceServer)
                            }

                            // --- Remote ICE candidates ---
                            val remoteIceJson = obj.getString("webice")
                            val remoteIceArray = org.json.JSONArray(remoteIceJson)
                            val remoteIce = Array(remoteIceArray.length()) { idx ->
                                val cand = remoteIceArray.getJSONObject(idx)
                                IceCandidate(
                                    cand.getString("sdpMid"),
                                    cand.getInt("sdpMLineIndex"),
                                    cand.getString("candidate")
                                )
                            }

                            // --- Remote SDP ---
                            val remoteSdp = obj.getString("websdp")

                            start(ctx, uid, token, iceServers, remoteIce, remoteSdp, localVideoTrack, onStateChanged, onDataMessage)
                        } catch (e: Exception) {
                            System.err.println("Error parsing WebRTC data1: ${e.message}")
                            Handler(ctx.mainLooper).post {
                                Toast.makeText(ctx, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Handler(ctx.mainLooper).post {
                            Toast.makeText(
                                ctx,
                                "Error fetching SDP: ${ex?.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                try {
                    println(data)
                    // --- TURN / STUN servers ---
                    val turnJson = data["turncreds"]!!
                    val turnObj = org.json.JSONObject(turnJson)
                    val serversArr = turnObj.getJSONArray("iceServers")
                    val iceServers = mutableListOf<PeerConnection.IceServer>()
                    for (i in 0 until serversArr.length()) {
                        val s = serversArr.getJSONObject(i)
                        val urls = s.getJSONArray("urls")
                        val urlList = mutableListOf<String>()
                        for (j in 0 until urls.length()) urlList.add(urls.getString(j))
                        val username = s.optString("username", null)
                        val credential = s.optString("credential", null)
                        val iceServer = if (username != null && credential != null) {
                            PeerConnection.IceServer.builder(urlList).setUsername(username).setPassword(credential).createIceServer()
                        } else {
                            PeerConnection.IceServer.builder(urlList).createIceServer()
                        }
                        iceServers.add(iceServer)
                    }

                    // --- Remote ICE candidates ---
                    val remoteIceJson = data["webice"]!!
                    val remoteIceArray = org.json.JSONArray(remoteIceJson)
                    val remoteIce = Array(remoteIceArray.length()) { idx ->
                        val cand = remoteIceArray.getJSONObject(idx)
                        IceCandidate(
                            cand.getString("sdpMid"),
                            cand.getInt("sdpMLineIndex"),
                            cand.getString("candidate")
                        )
                    }

                    // --- Remote SDP ---
                    val remoteSdp = data["websdp"]!!

                    start(ctx, uid, token, iceServers, remoteIce, remoteSdp, localVideoTrack, onStateChanged, onDataMessage)
                } catch (e: Exception) {
                    System.err.println("Error parsing WebRTC data: ${e.message}")
                    Handler(ctx.mainLooper).post {
                        Toast.makeText(ctx, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }


        /**
         * Builds and starts a peer connection using the provided json which must contain:
         *
         * It adds the localVideoTrack, creates an Answer, collects our ICE candidates
         * and then sends a POST /signal via EchoNetworkUtils.postSignal().
         */
        fun start(
            ctx: Context,
            uid: String,
            token: String,
            iceServers:MutableList<PeerConnection.IceServer>, remoteIce: Array<IceCandidate>, remoteSdp : String,
            localVideoTrack: VideoTrack,
            onStateChanged: (PeerConnection.IceConnectionState) -> Unit,
            onDataMessage: (String) -> Unit
        ) {
            if (!::peerConnectionFactory.isInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
                )
                peerConnectionFactory =
                    PeerConnectionFactory.builder().createPeerConnectionFactory()
            }


            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                iceTransportsType = PeerConnection.IceTransportsType.ALL

            }

            val thisConnectionIceCandidates = mutableListOf<IceCandidate>()

            lateinit var peerConnection: PeerConnection
            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    thisConnectionIceCandidates.add(candidate)
                    println("found candidate : ${thisConnectionIceCandidates.size}")
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {
                    //TODO("Not yet implemented")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    println("ICE Gathering State Changed: $state")
//                    if (state == PeerConnection.IceGatheringState.GATHERING) {
//                        Timer().schedule(timerTask {
//                            peerConnection.localDescription?.let { localSdp ->
//                                val iceCandidatesJson = org.json.JSONArray()
//                                thisConnectionIceCandidates.forEach { candidate ->
//                                    val candidateJson = org.json.JSONObject()
//                                    candidateJson.put("sdpMid", candidate.sdpMid)
//                                    candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex)
//                                    candidateJson.put("candidate", candidate.sdp)
//                                    iceCandidatesJson.put(candidateJson)
//                                }
//                                val ourIce = iceCandidatesJson.toString()
//                                GlobalScope.launch(Dispatchers.IO) {
//                                    EchoNetworkUtils.postSignal(
//                                        ctx,
//                                        uid,
//                                        token,
//                                        devicesdp = localSdp.description,
//                                        deviceice = ourIce
//                                    )
//                                }
//                            }
//                        },3000)
//                    }
                    if (state == PeerConnection.IceGatheringState.COMPLETE) {
                        peerConnection.localDescription?.let { localSdp ->
                            val iceCandidatesJson = org.json.JSONArray()
                            thisConnectionIceCandidates.forEach { candidate ->
                                val candidateJson = org.json.JSONObject()
                                candidateJson.put("sdpMid", candidate.sdpMid)
                                candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex)
                                candidateJson.put("candidate", candidate.sdp)
                                iceCandidatesJson.put(candidateJson)
                            }
                            val ourIce = iceCandidatesJson.toString()
                            GlobalScope.launch(Dispatchers.IO) {
                                EchoNetworkUtils.postSignal(
                                    ctx,
                                    uid,
                                    token,
                                    devicesdp = localSdp.description,
                                    deviceice = ourIce
                                )
                            }
                        }
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    if (state != null) onStateChanged(state)
                }

                override fun onDataChannel(dataChannel: DataChannel?) {
                    dataChannel?.registerObserver(object : DataChannel.Observer {
                        override fun onMessage(buffer: DataChannel.Buffer) {
                            val bytes = ByteArray(buffer.data.remaining())
                            buffer.data.get(bytes)
                            onDataMessage(String(bytes))
                        }

                        override fun onStateChange() {
                            println("Data channel state changed: ${dataChannel.state()}")
                            if (dataChannel.state() == DataChannel.State.OPEN) {
                                Timer().schedule(timerTask {
                                    println("Sending message on data channel")
                                    dataChannel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap("Hello from device".toByteArray()), false))
                                },3000,3000)
                            }
                        }
                        override fun onBufferedAmountChange(p0: Long) {}
                    })
                }

                override fun onAddStream(stream: MediaStream?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            })!!

            // Set remote description
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    remoteIce.forEach {
                        peerConnection.addIceCandidate(it)
                    }
                    // Add the outbound local track
                    localVideoTrack.setEnabled(true);
                    peerConnection.addTrack(localVideoTrack, listOf("ARDAMS"))
                    peerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            println("Created Answer SDP: ${answer.description}")
                            peerConnection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {}
                                override fun onSetFailure(p0: String?) {}
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, answer)
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, MediaConstraints())
                }

                override fun onSetFailure(p0: String?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onCreateSuccess(p0: SessionDescription?) {}
            }, offer)

            peerConnections[System.currentTimeMillis()] = peerConnection
        }


        /**
         * Closes and clears all created peer connections
         */
        fun closeAll() {
            peerConnections.forEach { it.value.close() }
            peerConnections.clear()
        }

        /**
         * Creates a VideoTrack that captures frames rendered into a VirtualDisplay.
         * The returned Pair<VideoTrack, Surface> contains:
         *  - a VideoTrack for WebRTC
         *  - the Surface you must pass when creating your VirtualDisplay
         *
         * Example:
         *   val (displayName,(videoTrack, surface)) = WebRTC.createVideoTrackForVirtualDisplay(ctx, width, height)
         *   val vDisplay = projection.createVirtualDisplay(..., surface, null, null)
         *   WebRTC.start(ctx, uid, token, json, videoTrack)
         */
        fun createVideoTrackForVirtualDisplay(
            ctx: Context,
            width: Int,
            height: Int
        ): Pair<String, Pair<VideoTrack, Surface>> {
            // init factory if needed
            if (!::peerConnectionFactory.isInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
                )
                peerConnectionFactory =
                    PeerConnectionFactory.builder().createPeerConnectionFactory()
            }

            val displayName = System.currentTimeMillis().toString()

            // Create EGL and surface helper
            val eglBase = EglBase.create()
            val surfaceTextureHelper =
                SurfaceTextureHelper.create("EchoCaptureThread-${displayName}", eglBase.eglBaseContext)

            // Create WebRTC VideoSource
            val videoSource = peerConnectionFactory.createVideoSource(true)

            // Create a Surface that will receive frames
            val surfaceTexture = surfaceTextureHelper.surfaceTexture
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)

            // Attach frames to the WebRTC capturerObserver
            surfaceTextureHelper.startListening { textureFrame ->
                println("Frame captured")
                val frame = VideoFrame(textureFrame.buffer, 0, textureFrame.timestampNs)
                videoSource.capturerObserver.onFrameCaptured(frame)
                frame.release()
            }


            val videoTrack =
                peerConnectionFactory.createVideoTrack(displayName, videoSource)

            return Pair(displayName, Pair(videoTrack, surface))
        }

        /**
         * Creates a VideoTrack that captures the screen using a MediaProjection.
         *
         * @param ctx The application context.
         * @param mediaProjection The MediaProjection instance obtained from the system.
         * @param width The desired capture width.
         * @param height The desired capture height.
         * @return A VideoTrack that can be added to a PeerConnection.
         */
        fun createTestVideoTrack(
            ctx: Context,
            intent: Intent,
            width: Int,
            height: Int
        ): VideoTrack {
            // Initialize PeerConnectionFactory if it hasn't been already
            if (!::peerConnectionFactory.isInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
                )
                peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            }

            // Create a video capturer that captures the screen
            val screenCapturer = ScreenCapturerAndroid(intent, object : MediaProjection.Callback() {
                override fun onStop() {
                    // Handle projection stop if needed
                    println("MediaProjection stopped")
                }
            })

            // Create a VideoSource from the factory
            // The 'true' argument indicates this is a screen cast
            val videoSource = peerConnectionFactory.createVideoSource(screenCapturer.isScreencast)

            // Initialize the capturer
            val surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", EglBase.create().eglBaseContext)
            screenCapturer.initialize(surfaceTextureHelper, ctx, videoSource.capturerObserver)

            // Start capturing frames
            // Note: 5000 is a common value for max frame rate in kbps, 30 is fps
            screenCapturer.startCapture(width, height, 30)

            // Create a VideoTrack from the VideoSource
            val videoTrack = peerConnectionFactory.createVideoTrack("ScreenCaptureTrack", videoSource)
            videoTrack.setEnabled(true)

            return videoTrack
        }

    }
}
