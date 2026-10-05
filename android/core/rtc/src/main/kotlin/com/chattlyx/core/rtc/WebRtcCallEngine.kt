package com.chattlyx.core.rtc

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CALL-01/02/04/06 WebRTC media engine (Apache-2.0 stream-webrtc prebuilt,
 * org.webrtc API). Signalling stays app-owned: offers/answers/candidates are
 * emitted as flows and carried by the E2EE call channel (zero-knowledge).
 */
class WebRtcCallEngine(
    private val context: Context,
    private val iceServers: List<String> = emptyList(),
) : CallEngine {

    private val _state = MutableStateFlow(CallState.IDLE)
    override val state: Flow<CallState> = _state.asStateFlow()

    override val eglContext: org.webrtc.EglBase.Context
        get() = rootEglBase.eglBaseContext

    private val _localSdpEvents = MutableSharedFlow<LocalSdp>(extraBufferCapacity = 8)
    override val localSdpEvents: Flow<LocalSdp> = _localSdpEvents.asSharedFlow()

    private val _iceCandidates = MutableSharedFlow<IceCandidate>(extraBufferCapacity = 64)
    override val iceCandidates: Flow<IceCandidate> = _iceCandidates.asSharedFlow()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    /** Attached renderers and whether each shows the remote feed. */
    private val renderers = LinkedHashMap<org.webrtc.SurfaceViewRenderer, Boolean>()
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoEnabled = false

    /** Shared GL context; the UI binds renderers to [rootEglBase]. */
    val rootEglBase: EglBase = EglBase.create()

    private val sdpConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        return PeerConnectionFactory.builder().createPeerConnectionFactory().also { factory = it }
    }

    override suspend fun startOutgoingCall(info: CallInfo): LocalSdp {
        videoEnabled = info.mediaType == CallMediaType.VIDEO
        setupPeerConnection()
        _state.value = CallState.CONNECTING
        val offer = createSessionDescription(isOffer = true)
        _localSdpEvents.emit(offer)
        return offer
    }

    override suspend fun acceptIncomingCall(info: CallInfo, offer: LocalSdp): LocalSdp {
        videoEnabled = info.mediaType == CallMediaType.VIDEO
        setupPeerConnection()
        _state.value = CallState.CONNECTING
        setRemoteDescription(offer.toWebrtc())
        val answer = createSessionDescription(isOffer = false)
        _localSdpEvents.emit(answer)
        return answer
    }

    override suspend fun applyRemoteSdp(sdp: LocalSdp) {
        setRemoteDescription(sdp.toWebrtc())
    }

    override suspend fun addRemoteCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(
            org.webrtc.IceCandidate(
                candidate.sdpMid ?: "0",
                candidate.sdpMLineIndex,
                candidate.candidate,
            ),
        )
    }

    override suspend fun endCall(callId: String) {
        renderers.forEach { (renderer, wasRemote) ->
            val track = if (wasRemote) remoteVideoTrack else videoTrack
            track?.removeSink(renderer)
        }
        renderers.clear()
        remoteVideoTrack = null
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        audioSource?.dispose()
        videoSource?.dispose()
        audioSource = null
        videoSource = null
        audioTrack = null
        videoTrack = null
        rootEglBase.release()
        _state.value = CallState.ENDED
    }

    override fun attachVideoRenderer(renderer: org.webrtc.SurfaceViewRenderer, remote: Boolean) {
        renderers[renderer] = remote
        val track = if (remote) remoteVideoTrack else videoTrack
        track?.addSink(renderer)
    }

    override fun detachVideoRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
        val wasRemote = renderers.remove(renderer) ?: return
        val track = if (wasRemote) remoteVideoTrack else videoTrack
        track?.removeSink(renderer)
    }

    override suspend fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    override suspend fun setSpeakerphone(enabled: Boolean) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.isSpeakerphoneOn = enabled
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        val track = videoTrack ?: return
        track.setEnabled(enabled)
        if (enabled) {
            runCatching { videoCapturer?.startCapture(720, 1280, 30) }
        } else {
            runCatching { videoCapturer?.stopCapture() }
        }
    }

    override suspend fun restartIce() {
        // CALL-06: renegotiate without dropping media state.
        val connection = peerConnection ?: return
        connection.restartIce()
    }

    // ------------------------------------------------------------------

    private fun setupPeerConnection() {
        val pcFactory = ensureFactory()
        val audioConstraints = MediaConstraints()
        val source = pcFactory.createAudioSource(audioConstraints)
        audioSource = source
        audioTrack = pcFactory.createAudioTrack("chattlyx-audio", source).apply { setEnabled(true) }

        if (videoEnabled) {
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                val vSource = pcFactory.createVideoSource(capturer.isScreencast)
                videoSource = vSource
                val helper = SurfaceTextureHelper.create("chattlyx-capture", rootEglBase.eglBaseContext)
                surfaceTextureHelper = helper
                capturer.initialize(helper, context, vSource.capturerObserver)
                videoTrack = pcFactory.createVideoTrack("chattlyx-video", vSource)
            }
        }

        val configuration = PeerConnection.RTCConfiguration(
            iceServers.map { PeerConnection.IceServer.builder(it).createIceServer() },
        ).apply {
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val connection = pcFactory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED,
                    -> _state.value = CallState.CONNECTED
                    PeerConnection.IceConnectionState.DISCONNECTED -> _state.value = CallState.RECONNECTING
                    PeerConnection.IceConnectionState.FAILED -> _state.value = CallState.FAILED
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: org.webrtc.IceCandidate) {
                _iceCandidates.tryEmit(
                    IceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex),
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dataChannel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() as? VideoTrack ?: return
                if (track.kind() != "video") return
                remoteVideoTrack = track
                // Late-attach any renderer the UI already put up for the feed.
                renderers.filterValues { remote -> remote }.keys.forEach { track.addSink(it) }
            }
        }) ?: error("PeerConnection creation failed")

        audioTrack?.let { connection.addTrack(it) }
        if (videoEnabled) videoTrack?.let { connection.addTrack(it) }
        peerConnection = connection

        if (videoEnabled) {
            runCatching { videoCapturer?.startCapture(720, 1280, 30) }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator(false)
        val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return null
        return enumerator.createCapturer(device, null)
    }

    private suspend fun createSessionDescription(isOffer: Boolean): LocalSdp {
        val connection = peerConnection ?: error("PeerConnection missing")
        val sdp = suspendCancellableCoroutine<SessionDescription> { continuation ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) =
                    continuation.resume(description)

                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) =
                    continuation.resumeWithException(IllegalStateException(error))

                override fun onSetFailure(error: String) = Unit
            }
            if (isOffer) connection.createOffer(observer, sdpConstraints)
            else connection.createAnswer(observer, sdpConstraints)
        }
        return LocalSdp(
            type = if (isOffer) SdpType.OFFER else SdpType.ANSWER,
            sdp = sdp.description,
        ).also {
            suspendCancellableCoroutine<Unit> { continuation ->
                connection.setLocalDescription(
                    object : SdpObserver {
                        override fun onCreateSuccess(description: SessionDescription?) = Unit
                        override fun onSetSuccess() = continuation.resume(Unit)
                        override fun onCreateFailure(error: String) = Unit
                        override fun onSetFailure(error: String) =
                            continuation.resumeWithException(IllegalStateException(error))
                    },
                    sdp,
                )
            }
        }
    }

    private suspend fun setRemoteDescription(sdp: SessionDescription) {
        val connection = peerConnection ?: error("PeerConnection missing")
        suspendCancellableCoroutine<Unit> { continuation ->
            connection.setRemoteDescription(
                object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onSetSuccess() = continuation.resume(Unit)
                    override fun onCreateFailure(error: String) = Unit
                    override fun onSetFailure(error: String) =
                        continuation.resumeWithException(IllegalStateException(error))
                },
                sdp,
            )
        }
    }

    private fun LocalSdp.toWebrtc(): SessionDescription = SessionDescription(
        when (type) {
            SdpType.OFFER -> SessionDescription.Type.OFFER
            SdpType.ANSWER -> SessionDescription.Type.ANSWER
        },
        sdp,
    )
}
