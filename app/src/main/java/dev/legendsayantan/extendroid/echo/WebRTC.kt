package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Handler
import android.view.Surface
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*

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
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {
                    //TODO("Not yet implemented")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) {
                        peerConnection.localDescription?.let { localSdp ->
                            val ourIce = thisConnectionIceCandidates.joinToString(";") {
                                "${it.sdpMid}:${it.sdpMLineIndex}:${it.sdp}"
                            }
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
                            //just when it opens, send a hi
                            if (dataChannel.state() == DataChannel.State.OPEN) {
                                dataChannel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap("Hello from device".toByteArray()), false))
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

            // Add the outbound local track
            peerConnection.addTrack(localVideoTrack,listOf("ARDAMS"))
            localVideoTrack.setEnabled(true)

            // OPTIONAL: create a data channel actively if you want one immediately
            peerConnection.createDataChannel("data", DataChannel.Init())

            // Set remote description
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    remoteIce.forEach {
                        peerConnection.addIceCandidate(it)
                    }
                    peerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
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
            val videoSource = peerConnectionFactory.createVideoSource(false)

            // Create a Surface that will receive frames
            val surfaceTexture = surfaceTextureHelper.surfaceTexture
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)

            // Attach frames to the WebRTC capturerObserver
            surfaceTextureHelper.startListening { textureFrame ->
                val frame = VideoFrame(textureFrame.buffer, 0, textureFrame.timestampNs)
                videoSource.capturerObserver.onFrameCaptured(frame)
                frame.release()
            }


            val videoTrack =
                peerConnectionFactory.createVideoTrack(displayName, videoSource)

            return Pair(displayName, Pair(videoTrack, surface))
        }

    }
}
