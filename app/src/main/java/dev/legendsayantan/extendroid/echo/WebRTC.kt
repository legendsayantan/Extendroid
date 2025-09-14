package dev.legendsayantan.extendroid.echo

import android.content.Context
import android.os.Handler
import android.widget.Toast
import dev.legendsayantan.extendroid.lib.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.Timer
import kotlin.concurrent.timerTask
import org.webrtc.PeerConnection.IceConnectionState.*;
import java.util.TimerTask

class WebRTC {
    companion object {

        private val eglBase = EglBase.create()
        private val peerConnections = hashMapOf<Long, PeerConnection>()

        fun getPeerConnectionCount():Int = peerConnections.size
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
                            logging.notify("Failed to start Echo session","Parse Error: ${e.message}","Echo")
                        }
                    } else {
                        logging.notify("Failed to start Echo session","Exception: ${ex?.message}","Echo")
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
                    logging.notify("Failed to start Echo session","Parse Error: ${e.message}","Echo")
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

            }

            val thisConnectionIceCandidates = mutableListOf<IceCandidate>()

            lateinit var peerConnection: PeerConnection
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {

                    // --- per-connection state for the countdown logic ---
                    private val gatherLock = Any()
                    private var gatherTimer: Timer? = null
                    private var gatherTimerTask: TimerTask? = null
                    private val candidateTimestamps = mutableListOf<Long>()
                    private var gatheringFinalized = false

                    // helper that packages & posts SDP + ICE candidates (runs once)
                    private fun postLocalSdpAndCandidates() {
                        synchronized(gatherLock) {
                            if (gatheringFinalized) return
                            gatheringFinalized = true
                            gatherTimerTask?.cancel()
                            gatherTimer?.cancel()
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
                                gatherTimerTask?.cancel()
                                gatherTimer?.cancel()
                            } catch (_: Exception) { /* ignore */ }

                            val now = System.currentTimeMillis()
                            // candidateTimestamps already updated by caller (onIceCandidate)
                            val count = candidateTimestamps.size.coerceAtLeast(1) // avoid division by zero

                            // compute max inter-arrival (ms)
                            val maxInterArrivalMs = if (candidateTimestamps.size >= 2) {
                                var maxDiff = 0L
                                for (i in 1 until candidateTimestamps.size) {
                                    val diff = candidateTimestamps[i] - candidateTimestamps[i - 1]
                                    if (diff > maxDiff) maxDiff = diff
                                }
                                // If somehow negative or zero, allow small minimum
                                maxDiff.coerceAtLeast(50L)
                            } else {
                                0L
                            }

                            // compute alternative: 30 / count seconds -> millis
                            val altMillis = ((30.0 / count) * 1000.0).toLong()

                            // countdown is the maximum of the two
                            val countdownMs = maxOf(maxInterArrivalMs, altMillis)

                            // Debug/log
                            logging.i("resetGatheringCountdown -> count=$count, maxInterArrivalMs=$maxInterArrivalMs, altMillis=$altMillis, countdownMs=$countdownMs","WebRTC.start")

                            // schedule new timer task
                            gatherTimer = Timer(true)
                            gatherTimerTask = timerTask {
                                // when countdown finishes, send SDP+ICE (only once)
                                postLocalSdpAndCandidates()
                            }
                            // schedule
                            gatherTimer?.schedule(gatherTimerTask, countdownMs)
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
                        logging.i("found candidate : ${thisConnectionIceCandidates.size}","WebRTC.start")

                        // Reset/start the countdown after each discovered candidate
                        resetGatheringCountdown()
                    }


                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {
                        p0?.forEach {
                            thisConnectionIceCandidates.remove(it)
                        }
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        logging.i("ICE Gathering State Changed: $state","WebRTC.start")
                        when (state) {
                            PeerConnection.IceGatheringState.COMPLETE -> {
                                // ICE finished normally: cancel countdown and send immediately (if not already sent)
                                postLocalSdpAndCandidates()
                            }
                            PeerConnection.IceGatheringState.GATHERING -> {
                                logging.i("ICE gathering started","WebRTC.start")
                            }
                            else -> {}
                        }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        if (state != null) onStateChanged(state)
                        if (listOf(FAILED, CLOSED, DISCONNECTED).contains(state)) {
                            peerConnections.remove(connectionId)
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
                                logging.i("Data channel state changed: ${dataChannel.state()}","WebRTC.start")
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

            if(capturer!=null && width>0 && height>0 && framerate>0) {
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
                    logging.i("Video capturer is null for peer connection ID: $connectionId","WebRTC.start")
                }
            }

            // Set remote description
            val offer = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    remoteIce.forEach {
                        peerConnection.addIceCandidate(it)
                    }

                    peerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            logging.i("Created Answer SDP: ${answer.description}","WebRTC.start")
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

            peerConnections[connectionId] = peerConnection

            EchoNetworkUtils.prepareMappings(ctx)
        }


        /**
         * Closes and clears all created peer connections
         */
        fun closeAll() {
            peerConnections.forEach { it.value.close() }
            peerConnections.clear()
        }

    }
}
