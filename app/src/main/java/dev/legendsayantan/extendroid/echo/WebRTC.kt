package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.legendsayantan.extendroid.lib.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.webrtc.PeerConnection.IceConnectionState.*;

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class WebRTC {
    companion object {

        // ICE DISCONNECTED is frequently transient (brief NAT rebind, network handover) and the
        // ICE agent keeps retrying connectivity checks on its own. Tearing the session down the
        // instant it happens made the connection far more fragile than the underlying network
        // actually was, so we wait this long for it to self-recover before giving up.
        private const val ICE_RECONNECT_GRACE_MS = 20_000L

        private val eglBase = EglBase.create()
        private val peerConnections = hashMapOf<Long, PeerConnection>()

        // --- Trickle ICE routing tables ---
        // sessionId -> PeerConnection: routes inbound FCM ICE candidate batches to the right PC
        private val sessionToPeer = ConcurrentHashMap<String, PeerConnection>()
        // sessionId -> buffered candidate JSON arrays (arrive before SDP is set)
        private val pendingCandidatesBySession = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
        // connectionId -> sessionId: for cleanup on closeConnection
        private val connectionIdToSession = ConcurrentHashMap<Long, String>()

        /**
         * Route an inbound trickle ICE candidate batch to the correct PeerConnection.
         * Called from CloudMessageService when an `icecandidates` FCM message arrives.
         */
        fun addIceCandidateForSession(sessionId: String, candidatesJson: String) {
            val arr = try { org.json.JSONArray(candidatesJson) } catch (e: Exception) { return }
            val pc = sessionToPeer[sessionId]
            if (pc == null) {
                // PC not yet registered: buffer until setRemoteDescription completes
                pendingCandidatesBySession
                    .getOrPut(sessionId) { CopyOnWriteArrayList() }
                    .add(candidatesJson)
                return
            }
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val candidate = IceCandidate(
                        obj.getString("sdpMid"),
                        obj.getInt("sdpMLineIndex"),
                        obj.getString("candidate")
                    )
                    Handler(Looper.getMainLooper()).post { pc.addIceCandidate(candidate) }
                } catch (e: Exception) { /* skip malformed candidate */ }
            }
        }

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
                            HardwareVideoEncoderFactory(
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
            sessionId: String,
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
                EchoNetworkUtils.getSignalWithCallback(ctx, uid, token, sessionId) { str, ex ->
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
                            // v2 response has no 'webice' — optString returns "[]" (empty array)
                            // v1 fetchsdp response still includes 'webice' — backward compat
                            val remoteIceJson = obj.optString("webice", "[]")
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
                                sessionId,
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
                    // v2 inline FCM path has no 'webice'; optString returns "[]" safely
                    val remoteIceJson = data["webice"] ?: "[]"
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
                        sessionId,
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
            sessionId: String,
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

            // --- per-connection state for trickle 50ms batching ---
            val localDescriptionSet = AtomicBoolean(false)
            val pendingLocalCandidates = mutableListOf<IceCandidate>()
            val localCandidateLock = Any()

            var batchHandler: Handler? = Handler(Looper.getMainLooper())
            var batchRunnable: Runnable? = null
            val batchBuffer = mutableListOf<IceCandidate>()
            val batchLock = Any()

            fun flushCandidateBatch() {
                val batch: List<IceCandidate>
                synchronized(batchLock) {
                    batch = batchBuffer.toList()
                    batchBuffer.clear()
                }
                if (batch.isEmpty()) return
                val arr = org.json.JSONArray()
                batch.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("sdpMid", c.sdpMid)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("candidate", c.sdp)
                    })
                }
                GlobalScope.launch(Dispatchers.IO) {
                    EchoNetworkUtils.sendIceCandidateBatch(
                        ctx, uid, token, sessionId, arr.toString()
                    )
                }
            }

            lateinit var peerConnection: PeerConnection
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {

                    // --- grace-period state for transient ICE disconnects ---
                    private var reconnectHandler: Handler? = Handler(Looper.getMainLooper())
                    private var reconnectRunnable: Runnable? = null

                    private fun cancelPendingDisconnectTimeout() {
                        reconnectRunnable?.let { reconnectHandler?.removeCallbacks(it) }
                        reconnectRunnable = null
                    }

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        if (candidate == null) return
                        if (!localDescriptionSet.get()) {
                            // Buffer candidates produced before setLocalDescription completes
                            synchronized(localCandidateLock) { pendingLocalCandidates.add(candidate) }
                            return
                        }
                        // Add to outbound 50ms batch
                        synchronized(batchLock) { batchBuffer.add(candidate) }
                        batchRunnable?.let { batchHandler?.removeCallbacks(it) }
                        batchRunnable = Runnable { flushCandidateBatch() }
                        batchHandler?.postDelayed(batchRunnable!!, 50L)
                    }

                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        // Logging only — ICE sending is now driven by the trickle 50ms batch mechanism
                        logging.i("ICE Gathering State Changed: $state", "WebRTC.start")
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
                                // Cancel the batch timer BEFORE closeConnection so no flush fires
                                // after the PC has been disposed.
                                synchronized(batchLock) {
                                    batchRunnable?.let { batchHandler?.removeCallbacks(it) }
                                    batchHandler = null
                                }
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
                    // Add any legacy inline ICE candidates (v1 backward compat; empty array for v2)
                    remoteIce.forEach { peerConnection.addIceCandidate(it) }

                    // Register PC so future trickle ICE candidates from FCM route here immediately
                    sessionToPeer[sessionId] = peerConnection
                    connectionIdToSession[connectionId] = sessionId

                    // Flush any trickle candidates that arrived before SDP was set (pre-SDP buffer)
                    val buffered = pendingCandidatesBySession.remove(sessionId) ?: emptyList()
                    for (json in buffered) {
                        addIceCandidateForSession(sessionId, json)
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
                                    localDescriptionSet.set(true)
                                    // Negotiation is now complete: sender.parameters.encodings
                                    // is populated and parameters can actually be applied.
                                    optimizeVideoEncoder(peerConnection, width, height, logging)

                                    // Flush pre-localDescription candidate buffer into the batch
                                    val toFlush: List<IceCandidate>
                                    synchronized(localCandidateLock) {
                                        toFlush = pendingLocalCandidates.toList()
                                        pendingLocalCandidates.clear()
                                    }
                                    synchronized(batchLock) { batchBuffer.addAll(toFlush) }

                                    GlobalScope.launch(Dispatchers.IO) {
                                        // 50ms window for pre-localDesc candidates then flush + send SDP
                                        delay(50.milliseconds)
                                        flushCandidateBatch()
                                        // POST SDP-only answer (no deviceice in v2)
                                        EchoNetworkUtils.postSignal(
                                            ctx, uid, token,
                                            sessionId = sessionId,
                                            devicesdp = peerConnection.localDescription?.description ?: return@launch
                                        )
                                    }
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
            sessionToPeer.clear()
            pendingCandidatesBySession.clear()
            connectionIdToSession.clear()
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

            // Trickle ICE cleanup: remove session routing entries
            val sid = connectionIdToSession.remove(connectionId)
            if (sid != null) {
                sessionToPeer.remove(sid)
                pendingCandidatesBySession.remove(sid)
            }
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
                lines.add(mLineIndex + 1, "b=AS:15000")
                lines.add(mLineIndex + 2, "b=TIAS:15000000")

                for (i in 0 until lines.size) {
                    val line = lines[i]
                    if (line.startsWith("a=fmtp:")) {
                        val pt = line.substringAfter("a=fmtp:").split(" ", limit = 2)[0].trim()
                        if (existingPayloads.contains(pt)) {
                            val sep = if (line.contains(" ")) (if (line.endsWith(";")) " " else "; ") else " "
                            lines[i] = "$line${sep}x-google-start-bitrate=2500;x-google-min-bitrate=1500;x-google-max-bitrate=15000"
                        }
                    }
                }

                val newSdp = lines.joinToString("\r\n")
                return newSdp
            } catch (e: Exception) {
                // on any failure, return original sdp
                logging.e( "preferLowestLatencyCodecInSdp failed: ${e.message}","WebRTC.preferLowestLatency")
                return sdp
            }
        }


        private fun optimizeVideoEncoder(peerConnection: PeerConnection, width: Int, height: Int, logging: Logging) {
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
                        try { encoding.networkPriority = 3 } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.networkPriority")
                        }
                        // Dynamic bitrate based on resolution with higher floor to prevent startup compression artifacts
                        val pixelCount = width * height
                        val minBitrate = (pixelCount * 1.0).toInt().coerceAtLeast(1_500_000)
                        val maxBitrate = (pixelCount * 5.0).toInt().coerceAtMost(20_000_000).coerceAtLeast(2_500_000)
                        
                        try { encoding.minBitrateBps = minBitrate } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.minBitrateBps")
                        }
                        try { encoding.maxBitrateBps = maxBitrate } catch (e: Exception) {
                            logging.e(e, "WebRTC.optimizeVideoEncoder.maxBitrateBps")
                        }
                    }

                    // For desktop and screencast streaming, maintain resolution so frames aren't downscaled or blurry during BWE ramps
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
