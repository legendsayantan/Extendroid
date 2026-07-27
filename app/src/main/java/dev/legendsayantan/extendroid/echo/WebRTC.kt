package dev.legendsayantan.extendroid.echo

import android.content.Context
import dev.legendsayantan.extendroid.lib.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.Timer
import kotlin.concurrent.timerTask
import org.webrtc.PeerConnection.IceConnectionState.*;
import java.util.Locale
import java.util.TimerTask

class WebRTC {
    companion object {

        // How long to wait, after the most recent ICE candidate, before finalizing gathering
        // and sending what we have - and the absolute cap on total gathering time regardless of
        // how many candidates keep trickling in. Replaces a previous "30 / candidateCount
        // seconds" heuristic whose wait time swung wildly depending on how many candidates a
        // given network happened to produce (could cut gathering short on sparse networks or
        // wait needlessly long on fast ones).
        private const val ICE_GATHER_DEBOUNCE_MS = 1500L
        private const val ICE_GATHER_MAX_TOTAL_MS = 5000L

        // ICE DISCONNECTED is frequently transient (brief NAT rebind, network handover) and the
        // ICE agent keeps retrying connectivity checks on its own. Tearing the session down the
        // instant it happens made the connection far more fragile than the underlying network
        // actually was, so we wait this long for it to self-recover before giving up.
        private const val ICE_RECONNECT_GRACE_MS = 20_000L

        private val eglBase = EglBase.create()
        private val peerConnections = hashMapOf<Long, PeerConnection>()

        fun getPeerConnectionCount(): Int = peerConnections.size
        private lateinit var peerConnectionFactory: PeerConnectionFactory
        private var videoCapturers = hashMapOf<Long, VideoCapturer>()
        private var surfaceTextureHelpers = hashMapOf<Long, SurfaceTextureHelper>()
        private var videoSources = hashMapOf<Long, VideoSource>()
        private var videoTracks = hashMapOf<Long, VideoTrack>()
        fun ensurePeerConnectionFactory(ctx: Context) {
            if (!::peerConnectionFactory.isInitialized) {
                val eglBaseContext = eglBase.eglBaseContext
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx)
                        .createInitializationOptions()
                )
                peerConnectionFactory =
                    PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(
                            DefaultVideoEncoderFactory(
                                eglBaseContext,
                                true,
                                true
                            )
                        )
                        .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
                        .createPeerConnectionFactory()
            }
        }

        fun checkAndStart(
            ctx: Context,
            connectionId: Long,
            uid: String,
            token: String,
            data: Map<String, String>,
            capturer: VideoCapturer?, width: Int, height: Int, framerate: Int,
            onStateChanged: (PeerConnection.IceConnectionState) -> Unit,
            dataChannelHandler: (DataChannel) -> Unit, onDataMessage: (String) -> Unit
        ) {
            val logging = Logging(ctx)
            if (data["fetchsdp"] == "true") {
                //if so, fetch the sdp from the backend
                EchoNetworkUtils.getSignalWithCallback(ctx, uid, token) { str, ex ->
                    if (str != null && ex == null) {
                        try {
                            // Not logging the raw body: it carries TURN credentials + SDP/ICE.
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
                                    PeerConnection.IceServer.builder(urlList).setUsername(username)
                                        .setPassword(credential).createIceServer()
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

                            start(
                                ctx, connectionId,
                                uid,
                                token,
                                iceServers,
                                remoteIce,
                                remoteSdp,
                                capturer, width, height, framerate,
                                onStateChanged,
                                dataChannelHandler, onDataMessage
                            )
                        } catch (e: Exception) {
                            logging.notify(
                                "Failed to start Echo session",
                                "Parse Error: ${e.message}",
                                "Echo"
                            )
                        }
                    } else {
                        logging.notify(
                            "Failed to start Echo session",
                            "Exception: ${ex?.message}",
                            "Echo"
                        )
                    }
                }
            } else {
                try {
                    // Not logging the raw payload: it carries TURN credentials + SDP/ICE.
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
                            PeerConnection.IceServer.builder(urlList).setUsername(username)
                                .setPassword(credential).createIceServer()
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

                    start(
                        ctx, connectionId,
                        uid,
                        token,
                        iceServers,
                        remoteIce,
                        remoteSdp,
                        capturer, width, height, framerate,
                        onStateChanged,
                        dataChannelHandler, onDataMessage
                    )
                } catch (e: Exception) {
                    logging.notify(
                        "Failed to start Echo session",
                        "Parse Error: ${e.message}",
                        "Echo"
                    )
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
            connectionId: Long,
            uid: String,
            token: String,
            iceServers: MutableList<PeerConnection.IceServer>,
            remoteIce: Array<IceCandidate>,
            remoteSdp: String,
            capturer: VideoCapturer?, width: Int, height: Int, framerate: Int,
            onStateChanged: (PeerConnection.IceConnectionState) -> Unit,
            dataChannelhandler: (DataChannel) -> Unit, onDataMessage: (String) -> Unit
        ) {
            ensurePeerConnectionFactory(ctx)
            val logging = Logging(ctx)
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                enableDscp = false
            }

            val thisConnectionIceCandidates = mutableListOf<IceCandidate>()

            lateinit var peerConnection: PeerConnection
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {

                    // --- per-connection state for the countdown logic ---
                    private val gatherLock = Any()
                    private var gatherHandler: android.os.Handler? = android.os.Handler(android.os.Looper.getMainLooper())
                    private var gatherRunnable: Runnable? = null
                    private val candidateTimestamps = mutableListOf<Long>()
                    private var gatheringFinalized = false

                    // --- grace-period state for transient ICE disconnects ---
                    private var reconnectHandler: android.os.Handler? = android.os.Handler(android.os.Looper.getMainLooper())
                    private var reconnectRunnable: Runnable? = null

                    private fun cancelPendingDisconnectTimeout() {
                        reconnectRunnable?.let { reconnectHandler?.removeCallbacks(it) }
                        reconnectRunnable = null
                    }

                    // helper that packages & posts SDP + ICE candidates (runs once)
                    private fun postLocalSdpAndCandidates() {
                        synchronized(gatherLock) {
                            if (gatheringFinalized) return
                            gatheringFinalized = true
                            gatherRunnable?.let { gatherHandler?.removeCallbacks(it) }
                        }

                        peerConnection.localDescription?.let { localSdp ->
                            val iceCandidatesJson = org.json.JSONArray()
                            thisConnectionIceCandidates.forEach { candidate ->
                                val candidateJson = org.json.JSONObject().apply {
                                    put("sdpMid", candidate.sdpMid)
                                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                                    put("candidate", candidate.sdp)
                                }
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

                    // schedule/reset countdown based on recorded timestamps & candidate count
                    private fun resetGatheringCountdown() {
                        synchronized(gatherLock) {
                            // Don't schedule if we already finalized (e.g. COMPLETE or timer fired)
                            if (gatheringFinalized) return

                            // cancel previous timer/task
                            try {
                                gatherRunnable?.let { gatherHandler?.removeCallbacks(it) }
                            } catch (_: Exception) { /* ignore */
                            }

                            // candidateTimestamps already updated by caller (onIceCandidate)
                            // Bounded debounce: wait a short quiet period after the most recent
                            // candidate, but never past an absolute cap measured from the first
                            // candidate, so gathering always terminates in predictable time.
                            val firstCandidateAt = candidateTimestamps.firstOrNull() ?: System.currentTimeMillis()
                            val elapsedSinceFirst = System.currentTimeMillis() - firstCandidateAt
                            val remainingUntilCap = (ICE_GATHER_MAX_TOTAL_MS - elapsedSinceFirst).coerceAtLeast(0L)
                            val countdownMs = minOf(ICE_GATHER_DEBOUNCE_MS, remainingUntilCap)

                            // Debug/log
                            logging.d(
                                "resetGatheringCountdown -> candidates=${candidateTimestamps.size}, countdownMs=$countdownMs",
                                "WebRTC.start"
                            )

                            // schedule new timer task
                            gatherRunnable = Runnable {
                                // when countdown finishes, send SDP+ICE (only once)
                                postLocalSdpAndCandidates()
                            }
                            // schedule
                            gatherHandler?.postDelayed(gatherRunnable!!, countdownMs)
                        }
                    }

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        if (candidate == null) return
                        // add candidate and timestamp, then reset countdown
                        thisConnectionIceCandidates.add(candidate)
                        val now = System.currentTimeMillis()
                        synchronized(gatherLock) {
                            candidateTimestamps.add(now)
                        }
                        logging.d(
                            "found candidate : ${thisConnectionIceCandidates.size}",
                            "WebRTC.start"
                        )

                        // Reset/start the countdown after each discovered candidate
                        resetGatheringCountdown()
                    }


                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {
                        p0?.forEach {
                            thisConnectionIceCandidates.remove(it)
                        }
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        logging.i("ICE Gathering State Changed: $state", "WebRTC.start")
                        when (state) {
                            PeerConnection.IceGatheringState.COMPLETE -> {
                                // ICE finished normally: cancel countdown and send immediately (if not already sent)
                                postLocalSdpAndCandidates()
                            }

                            PeerConnection.IceGatheringState.GATHERING -> {
                                logging.i("ICE gathering started", "WebRTC.start")
                            }

                            else -> {}
                        }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        if (state == null) return
                        when (state) {
                            DISCONNECTED -> {
                                // Don't tear down immediately: this is often a brief blip (network
                                // handover, NAT rebind) that the ICE agent recovers from by itself.
                                // Give it a grace window before treating it as a real failure.
                                logging.i(
                                    "ICE disconnected, waiting up to ${ICE_RECONNECT_GRACE_MS}ms for recovery",
                                    "WebRTC.start"
                                )
                                cancelPendingDisconnectTimeout()
                                reconnectRunnable = Runnable {
                                    logging.i("ICE did not recover in time, closing connection", "WebRTC.start")
                                    onStateChanged(FAILED)
                                    closeConnection(connectionId)
                                }
                                reconnectHandler?.postDelayed(reconnectRunnable!!, ICE_RECONNECT_GRACE_MS)
                            }

                            CONNECTED, COMPLETED -> {
                                cancelPendingDisconnectTimeout()
                                onStateChanged(state)
                            }

                            FAILED, CLOSED -> {
                                cancelPendingDisconnectTimeout()
                                onStateChanged(state)
                                closeConnection(connectionId)
                            }

                            else -> onStateChanged(state)
                        }
                    }

                    override fun onDataChannel(dataChannel: DataChannel?) {
                        dataChannel?.registerObserver(object : DataChannel.Observer {
                            override fun onMessage(buffer: DataChannel.Buffer) {
                                val bytes = ByteArray(buffer.data.remaining())
                                buffer.data.get(bytes)
                                onDataMessage(String(bytes))
                            }

                            override fun onStateChange() {
                                logging.i(
                                    "Data channel state changed: ${dataChannel.state()}",
                                    "WebRTC.start"
                                )
                                if (dataChannel.state() == DataChannel.State.OPEN) {
                                    dataChannelhandler(dataChannel)
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
                }
            )!!

            if (capturer != null && width > 0 && height > 0 && framerate > 0) {
                //create video track
                surfaceTextureHelpers[connectionId] =
                    SurfaceTextureHelper.create(connectionId.toString(), eglBase.eglBaseContext)
                videoCapturers[connectionId] = capturer
                videoSources[connectionId] =
                    peerConnectionFactory.createVideoSource(videoCapturers[connectionId]!!.isScreencast)
                videoCapturers[connectionId]?.initialize(
                    surfaceTextureHelpers[connectionId],
                    ctx,
                    videoSources[connectionId]!!.capturerObserver
                )
                videoTracks[connectionId] =
                    peerConnectionFactory.createVideoTrack(
                        "video_track_$connectionId",
                        videoSources[connectionId]!!
                    )

                peerConnection.addTrack(videoTracks[connectionId])
                videoCapturers[connectionId]?.startCapture(
                    width, // Width
                    height, // Height
                    framerate // FPS
                ) ?: run {
                    logging.i(
                        "Video capturer is null for peer connection ID: $connectionId",
                        "WebRTC.start"
                    )
                }
            }

            // Set remote description
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    remoteIce.forEach {
                        peerConnection.addIceCandidate(it)
                    }

                    // Explicitly declare this as send-only: we don't receive audio/video
                    val answerConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    peerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            logging.d("Created Answer SDP: ${answer.description}", "WebRTC.start")
                            peerConnection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    // Negotiation is now complete: sender.parameters.encodings
                                    // is populated and parameters can actually be applied.
                                    optimizeVideoEncoder(peerConnection, logging)
                                }
                                override fun onSetFailure(p0: String?) {}
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, SessionDescription(answer.type, preferLowestLatencyCodecInSdp(answer.description, logging)))
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, answerConstraints)
                }

                override fun onSetFailure(p0: String?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onCreateSuccess(p0: SessionDescription?) {}
            }, offer)

            peerConnections[connectionId] = peerConnection

            EchoNetworkUtils.prepareMappings(ctx)
        }


        /**
         * Closes and clears all created peer connections
         */
        fun closeAll() {
            peerConnections.keys.toList().forEach { closeConnection(it) }
        }

        fun closeConnection(connectionId: Long) {
            videoCapturers.remove(connectionId)?.let {
                it.stopCapture()
                it.dispose()
            }
            videoTracks.remove(connectionId)?.dispose()
            videoSources.remove(connectionId)?.dispose()
            surfaceTextureHelpers.remove(connectionId)?.dispose()
            peerConnections.remove(connectionId)?.dispose()
        }

        // Whether this device's hardware encoder actually accelerates H264, rather than assuming
        // every Android device does - some only have hardware VP8/VP9 and would silently fall
        // back to a slow software H264 encoder if we forced that preference blindly.
        private fun deviceSupportsHardwareH264(): Boolean {
            return try {
                HardwareVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                    .supportedCodecs.any { it.name.equals("H264", ignoreCase = true) }
            } catch (e: Exception) {
                false
            }
        }

        // Kotlin: preferLowestLatencyCodecInSdp(offerSdp)
        // Returns modified SDP where m=video payloads are reordered to prefer H264 on mobile, else VP8.
        private fun preferLowestLatencyCodecInSdp(sdp: String,logging: Logging): String {
            try {
                val preferH264 = deviceSupportsHardwareH264()
                val lines = sdp.split("\r\n").toMutableList()

                val mLineIndex = lines.indexOfFirst { it.startsWith("m=video ") }
                if (mLineIndex == -1) return sdp

                // collect payload types for codecs
                val h264Pts = mutableListOf<String>()
                val vp8Pts = mutableListOf<String>()
                val vp9Pts = mutableListOf<String>()

                for (line in lines) {
                    if (line.startsWith("a=rtpmap:")) {
                        val parts = line.substringAfter("a=rtpmap:").split(" ", limit = 2)
                        if (parts.size >= 2) {
                            val pt = parts[0].trim()
                            val codec = parts[1].split("/", limit = 2)[0].trim()
                                .lowercase(Locale.ROOT)
                            when {
                                // H.263 is an unrelated legacy codec — do NOT group with H.264.
                                // It falls through to the leftover bucket and is deprioritised.
                                codec.contains("h264") -> h264Pts.add(pt)
                                codec.contains("vp8") -> vp8Pts.add(pt)
                                codec.contains("vp9") -> vp9Pts.add(pt)
                            }
                        }
                    }
                }

                // Build new order based on preference
                val existingParts = lines[mLineIndex].split(" ").toMutableList()
                val existingPayloads = existingParts.subList(3, existingParts.size)

                val newOrder = mutableListOf<String>()
                if (preferH264) {
                    newOrder.addAll(h264Pts.filter { existingPayloads.contains(it) })
                    newOrder.addAll(vp8Pts.filter { existingPayloads.contains(it) })
                    newOrder.addAll(vp9Pts.filter { existingPayloads.contains(it) })
                } else {
                    newOrder.addAll(vp8Pts.filter { existingPayloads.contains(it) })
                    newOrder.addAll(h264Pts.filter { existingPayloads.contains(it) })
                    newOrder.addAll(vp9Pts.filter { existingPayloads.contains(it) })
                }
                // Append leftover payloads that weren't covered
                for (pt in existingPayloads) {
                    if (!newOrder.contains(pt)) newOrder.add(pt)
                }

                val newMLine = (existingParts.subList(0, 3) + newOrder).joinToString(" ")
                lines[mLineIndex] = newMLine

                val newSdp = lines.joinToString("\r\n")
                return newSdp
            } catch (e: Exception) {
                // on any failure, return original sdp
                logging.e( "preferLowestLatencyCodecInSdp failed: ${e.message}","WebRTC.preferLowestLatency")
                return sdp
            }
        }


        private fun optimizeVideoEncoder(peerConnection: PeerConnection, logging: Logging) {
            try {
                val videoSender = peerConnection.senders.firstOrNull {
                    it.track()?.kind() == "video"
                } ?: return

                val params = videoSender.parameters
                if (params.encodings.isNotEmpty()) {
                    params.encodings.forEach { encoding ->
                        try { encoding.scaleResolutionDownBy = 1.0 } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.scaleResolutionDownBy")
                        }
                        // Priority values: VERY_LOW=0, LOW=1, MEDIUM=2, HIGH=3.
                        // Was incorrectly set to 1 (LOW). Use 3 for HIGH priority.
                        try { encoding.networkPriority = 3 } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.networkPriority")
                        }
                        // Bitrate floor prevents GCC from starving the stream on transient
                        // congestion; ceiling prevents encoder buffer build-up.
                        try { encoding.minBitrateBps = 200_000 } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.minBitrateBps")
                        }
                        try { encoding.maxBitrateBps = 4_000_000 } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.maxBitrateBps")
                        }
                    }

                    // Enable low latency mode in codec settings
                    params.degradationPreference =
                        RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE

                    videoSender.parameters = params
                }
            } catch (e: Exception) {
                logging.e("optimizeVideoEncoder failed: ${e.message}", "WebRTC")
            }
        }

    }
}
