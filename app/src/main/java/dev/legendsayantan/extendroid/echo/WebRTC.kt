package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*

class WebRTC {
    companion object {

        private val peerConnections = hashMapOf<Long, PeerConnection>()
        private lateinit var peerConnectionFactory: PeerConnectionFactory

        /**
         * Builds and starts a peer connection using the provided json which must contain:
         *   - turncreds : JSON string with "iceServers"
         *   - websdp    : remote offer SDP
         *   - webice    : array of ICE candidates from the remote peer
         *
         * It adds the localVideoTrack, creates an Answer, collects our ICE candidates
         * and then sends a POST /signal via EchoNetworkUtils.postSignal().
         */
        fun start(
            ctx: Context,
            uid: String,
            token: String,
            json: String,
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

            val obj = JSONObject(json)
            val turnCredsJson = JSONObject(obj.getString("turncreds"))
            val remoteSdp = obj.getString("websdp")
            val remoteIceArray = JSONArray(obj.getString("webice"))

            val iceServers = mutableListOf<PeerConnection.IceServer>()
            val servers = turnCredsJson.getJSONArray("iceServers")
            for (i in 0 until servers.length()) {
                val s = servers.getJSONObject(i)
                val urlsJson = s.getJSONArray("urls")
                val urls = List(urlsJson.length()) { idx -> urlsJson.getString(idx) }
                val builder = PeerConnection.IceServer.builder(urls)
                if (s.has("username")) builder.setUsername(s.getString("username"))
                if (s.has("credential")) builder.setPassword(s.getString("credential"))
                iceServers.add(builder.createIceServer())
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

                        override fun onStateChange() {}
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
            peerConnection.addTrack(localVideoTrack)

            // OPTIONAL: create a data channel actively if you want one immediately
            peerConnection.createDataChannel("echo-data", DataChannel.Init())

            // Set remote description
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    for (i in 0 until remoteIceArray.length()) {
                        val c = remoteIceArray.getJSONObject(i)
                        peerConnection.addIceCandidate(
                            IceCandidate(
                                c.getString("sdpMid"),
                                c.getInt("sdpMLineIndex"),
                                c.getString("candidate")
                            )
                        )
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
            surfaceTextureHelper.startListening { frame ->
                videoSource.capturerObserver.onFrameCaptured(frame)
            }

            val videoTrack =
                peerConnectionFactory.createVideoTrack(displayName, videoSource)

            return Pair(displayName, Pair(videoTrack, surface))
        }

    }
}
