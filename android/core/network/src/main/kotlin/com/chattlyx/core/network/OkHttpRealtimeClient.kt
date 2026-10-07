package com.chattlyx.core.network

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.network.rest.ApiEndpoints
import com.chattlyx.proto.AuthFrame
import com.chattlyx.proto.Frame
import com.chattlyx.proto.PingFrame
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import timber.log.Timber

/**
 * OkHttp-backed realtime socket (MSG-07): AuthFrame on open, adaptive
 * heartbeat, exponential reconnect with jitter, typed frame fan-out.
 */
@Singleton
class OkHttpRealtimeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val connectivityMonitor: ConnectivityMonitor,
    @Dispatcher(ChattlyxDispatcher.IO) private val dispatcher: CoroutineDispatcher,
) : RealtimeClient {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<Frame>(extraBufferCapacity = FRAME_BUFFER)
    override val incomingFrames: SharedFlow<Frame> = _incomingFrames.asSharedFlow()

    private var socket: WebSocket? = null
    private var accessToken: String? = null
    private var deviceId: Long = 0L
    private var autoReconnect = false
    private var attempt = 0
    private var heartbeatJob: Job? = null

    private val backoff = ReconnectBackoff()
    private val heartbeat = AdaptiveHeartbeat()

    override suspend fun connect(accessToken: String, deviceId: Long) {
        this.accessToken = accessToken
        this.deviceId = deviceId
        autoReconnect = true
        attempt = 0
        openSocket()
    }

    override suspend fun disconnect() {
        autoReconnect = false
        heartbeatJob?.cancel()
        socket?.close(NORMAL_CLOSURE, "client disconnect")
        socket = null
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    override fun requestReconnect() {
        if (!autoReconnect) return
        attempt = 0
        socket?.cancel()
        scope.launch { openSocket() }
    }

    override suspend fun send(frame: Frame): Boolean {
        val ws = socket ?: return false
        return ws.send(frame.toByteArray().toByteString())
    }

    private fun openSocket() {
        val token = accessToken ?: return
        if (_connectionState.value == RealtimeConnectionState.CONNECTED) return

        val status = connectivityMonitor.snapshot()
        if (!status.isUsable) {
            Timber.d("Realtime connect deferred: network unavailable")
            scheduleReconnect()
            return
        }

        _connectionState.value = RealtimeConnectionState.CONNECTING
        val wsUrl = ApiEndpoints.BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "v1/ws"

        socket = okHttpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            SocketListener(token),
        )
    }

    private inner class SocketListener(private val token: String) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = RealtimeConnectionState.AUTHENTICATING
            val authFrame = Frame.newBuilder()
                .setAuth(
                    AuthFrame.newBuilder()
                        .setAccessToken(token)
                        .setDeviceId(deviceId.toInt()),
                )
                .build()
            webSocket.send(authFrame.toByteArray().toByteString())

            // Server accepts silently; first data confirms the session.
            _connectionState.value = RealtimeConnectionState.CONNECTED
            attempt = 0
            startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            val frame = try {
                Frame.parseFrom(bytes.toByteArray())
            } catch (e: Exception) {
                Timber.w(e, "Unparseable realtime frame dropped")
                return
            }
            _incomingFrames.tryEmit(frame)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.i("Realtime socket failure; scheduling reconnect")
            handleDrop()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.i("Realtime socket closed code=%d", code)
            handleDrop()
        }

        private fun handleDrop() {
            heartbeatJob?.cancel()
            socket = null
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            if (autoReconnect) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val delayMillis = backoff.delayMillis(attempt)
        attempt += 1
        scope.launch {
            delay(delayMillis)
            if (autoReconnect) openSocket()
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                val status = connectivityMonitor.snapshot()
                val interval = heartbeat.intervalMillis(
                    networkType = status.type,
                    foreground = true,
                    batterySaver = false,
                )
                delay(interval)
                val ping = Frame.newBuilder()
                    .setPing(PingFrame.newBuilder().setClientTimestampMs(System.currentTimeMillis()))
                    .build()
                if (!webSocket.send(ping.toByteArray().toByteString())) {
                    Timber.i("Heartbeat send failed; dropping socket")
                    webSocket.cancel()
                    return@launch
                }
            }
        }
    }

    private companion object {
        const val FRAME_BUFFER = 64
        const val NORMAL_CLOSURE = 1000
    }
}
